package osgi.enroute.trains.track.manager.example.provider;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import osgi.enroute.dto.api.DTOs;
import osgi.enroute.scheduler.api.Scheduler;
import osgi.enroute.trains.cloud.api.Color;
import osgi.enroute.trains.cloud.api.Command;
import osgi.enroute.trains.cloud.api.Observation;
import osgi.enroute.trains.cloud.api.Segment;
import osgi.enroute.trains.cloud.api.TrackConfiguration;
import osgi.enroute.trains.cloud.api.TrackForSegment;
import osgi.enroute.trains.cloud.api.TrackForTrain;
import osgi.enroute.trains.cloud.api.TrackInfo;
import osgi.enroute.trains.track.util.Tracks;
import osgi.enroute.trains.track.util.Tracks.LocatorHandler;
import osgi.enroute.trains.track.util.Tracks.SegmentHandler;
import osgi.enroute.trains.track.util.Tracks.SignalHandler;
import osgi.enroute.trains.track.util.Tracks.SwitchHandler;

/**
 * 
 */
@Component(name = TrackConfiguration.TRACK_CONFIGURATION_PID, service = { TrackForSegment.class, TrackForTrain.class,
		TrackInfo.class })
public class ExampleTrackManagerImpl implements TrackForSegment, TrackForTrain {
	static Logger logger = LoggerFactory.getLogger(ExampleTrackManagerImpl.class);
	static Random random = new Random();

	private Map<String, String> trains = new HashMap<String, String>();

	private List<Observation> observations = new ArrayList<Observation>();

	@Reference
	private EventAdmin ea;
	@Reference
	private DTOs dtos;
	@Reference
	private Scheduler scheduler;

	private Tracks<Object> track;
	private int offset;
	private Closeable ticker;

	@Activate
	public void activate(TrackConfiguration config) throws Exception{
		track = new Tracks<Object>(config.segments(), new TrackManagerFactory(this));
		
		for ( String train : config.trains()) {
			String parts[] = train.split("\\s*:\\s*");
			if ( parts.length != 2)
				throw new IllegalArgumentException("Invalid train name, must be <rfid>:<name>");
			trains.put(parts[0], parts[1]);
		}
		
		ticker = scheduler.schedule( this::tick, 1000);
	}
	
	@Deactivate
	void deactivate( ) throws IOException {
		ticker.close();
	}
	
	
	/*
	 * Random mutator for seeing if the GUI works
	 */
	
	void tick() {
		Collection<? extends SegmentHandler<Object>> handlers = track.getHandlers();
		int r = random.nextInt(handlers.size());
		for ( SegmentHandler<Object> sh : handlers) {
			if ( r == 0 ) {
				if (sh instanceof SignalHandler) {
					SignalHandler<Object> signal = (SignalHandler<Object>) sh;
					if ( signal.color == null)
						signal.color = Color.RED;

					if ( signal.color == Color.RED) {
						setSignal(signal.segment.id, Color.GREEN);
						scheduler.after(()-> setSignal(signal.segment.id,Color.YELLOW),8000);
						scheduler.after(()-> setSignal(signal.segment.id,Color.RED),12000);
					}
				} else if ( sh instanceof Switch) {
					SwitchHandler<Object> swtch = (SwitchHandler<Object>) sh;
					doSwitch(swtch.segment.id);
				}
				return;
			}
			r--;
		}
	}


	private void setSignal(String segmentId, Color color) {
		Command c = new Command();
		c.type=Command.Type.SIGNAL;
		c.segment = segmentId;
		c.signal = color;
		command(c);
	}

	private void doSwitch(String segmentId) {
		Command c = new Command();
		c.type = Command.Type.SWITCH;
		c.segment = segmentId;
		command(c);
	}


	@Override
	public Map<String, Segment> getSegments() {
		return track.getSegments();
	}

	@Override
	public List<String> getTrains() {
		return new ArrayList<String>(trains.values());
	}

	@Override
	public Map<String, Color> getSignals() {
		return track.filter(SignalHandler.class).collect(Collectors.toMap( sh -> sh.segment.id, sh -> sh.color));
	}

	@Override
	public Map<String, Boolean> getSwitches() {
		return track.filter(SwitchHandler.class).collect(Collectors.toMap( sh -> sh.segment.id, sh -> sh.toAlternate));
	}

	@Override
	public Map<String, String> getLocators() {
		return track.filter(LocatorHandler.class).collect(Collectors.toMap( lh -> lh.segment.id, lh -> lh.lastSeenId));
	}

	@Override
	public List<Observation> getRecentObservations(long sinceId) {
		return observations.subList((int) sinceId, observations.size());
	}

	@Override
	public String getAssignment(String train) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean requestAccessTo(String train, String fromSegment, String toSegment) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void registerTrain(String id, String type) {
		System.out.println("Train " + id + " online!");
	}

	@Override
	public void locatedTrainAt(String rfid, String segment) {
		String train = trains.get(rfid);
		if ( train == null)
			throw new IllegalArgumentException("Unknown train for rfid " + rfid);

		Locator handler = track.getHandler(Locator.class, segment);
		handler.locatedAt(rfid);
	}

	@Override
	public void switched(String segment, boolean alternative) {
		Switch swtch = track.getHandler(Switch.class, segment);
		swtch.alternative(alternative);
	}

	@Override
	public void signal(String segment, Color color) {
		Signal signal = track.getHandler(Signal.class, segment);
		signal.setColor(color);
	}

	void observation(Observation o) {
		try {
			o.time = System.currentTimeMillis();
			synchronized(observations) {
				o.id = offset + observations.size();
				observations.add(o);
			}
			Event event = new Event(Observation.TOPIC, dtos.asMap(o));
			ea.postEvent(event);
		} catch (Exception e) {
			logger.error("Error posting observation " + o, e);
		}
	}

	void command(Command c){
		try {
			Event event = new Event(Command.TOPIC, dtos.asMap(c));
			ea.postEvent(event);
		} catch (Exception e) {
			logger.error("Error posting command " + c, e);
		}
	}
	
	@Override
	public Set<String> getBlocked() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void blocked(String segment, String reason, boolean blocked) {
		// TODO Auto-generated method stub

	}
}
