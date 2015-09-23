package osgi.enroute.trains.track.manager.example.provider;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import osgi.enroute.trains.cloud.api.Observation.Type;
import osgi.enroute.trains.track.util.Tracks;
import osgi.enroute.trains.track.util.Tracks.LocatorHandler;
import osgi.enroute.trains.track.util.Tracks.SegmentHandler;
import osgi.enroute.trains.track.util.Tracks.SignalHandler;
import osgi.enroute.trains.track.util.Tracks.SwitchHandler;

/**
 * 
 */
@Component(name = TrackConfiguration.TRACK_CONFIGURATION_PID, 
		service = { TrackForSegment.class, TrackForTrain.class,TrackInfo.class, Object.class },
		property={"osgi.command.scope=trains",
		"osgi.command.function=assign"})
public class ExampleTrackManagerImpl implements TrackForSegment, TrackForTrain {
	static Logger logger = LoggerFactory.getLogger(ExampleTrackManagerImpl.class);
	static Random random = new Random();

	private Map<String, String> trains = new HashMap<String, String>();
	private List<Observation> observations = new ArrayList<Observation>();

	// train assignments train->segment
	private Map<String, String> assignments = new HashMap<String, String>();
	// track access track->train
	private Map<String, String> access = new HashMap<String, String>();
	// blocked segments
	private Set<String> blocked = new HashSet<String>();
	
	static final int TIMEOUT = 60000;

	
	@Reference
	private EventAdmin ea;
	@Reference
	private DTOs dtos;
	@Reference
	private Scheduler scheduler;

	private Tracks<Object> tracks;
	private int offset;
	private Closeable ticker;

	@Activate
	public void activate(TrackConfiguration config) throws Exception{
		tracks = new Tracks<Object>(config.segments(), new TrackManagerFactory(this));
	}
	
	@Deactivate
	void deactivate( ) throws IOException {
		ticker.close();
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

	public void assign(String train, String segmentId){
		SegmentHandler sh = tracks.getHandler(segmentId);
		if(sh==null){
			logger.error("No valid segment id given.");
			return;
		}
		if(!sh.isLocator()){
			logger.error("Only locator segments can be used for assignments.");
			return;
		}
		if(!getTrains().contains(train)){
			logger.error("Train "+train+" is not registered.");
			return;	
		}

		assignments.put(train, segmentId);
		
		Observation o = new Observation();
		o.type = Observation.Type.ASSIGNMENT;
		o.train = train;
		o.assignment = segmentId;
		observation(o);
	}
	
	@Override
	public Map<String, Segment> getSegments() {
		return tracks.getSegments();
	}

	@Override
	public List<String> getTrains() {
		return new ArrayList<String>(trains.values());
	}

	@Override
	public Map<String, Color> getSignals() {
		return tracks.filter(SignalHandler.class).collect(Collectors.toMap( sh -> sh.segment.id, sh -> sh.color));
	}

	@Override
	public Map<String, Boolean> getSwitches() {
		return tracks.filter(SwitchHandler.class).collect(Collectors.toMap( sh -> sh.segment.id, sh -> sh.toAlternate));
	}

	@Override
	public Map<String, String> getLocators() {
		return tracks.filter(LocatorHandler.class).collect(Collectors.toMap( lh -> lh.segment.id, lh -> lh.lastSeenId));
	}

	@Override
	public List<Observation> getRecentObservations(long sinceId) {
		List<Observation> o = new ArrayList<Observation>();
		synchronized(observations){
			while(sinceId+1 >= observations.size()){
				try {
					observations.wait(60000);
				} catch (InterruptedException e) {
				}
			}	
			if(sinceId+1 < observations.size()){
				o.addAll(observations.subList((int) (sinceId+1), observations.size()));
			}
		}
		return o;
	}

	@Override
	public String getAssignment(String train) {
		return assignments.get(train);
	}

	@Override
	public boolean requestAccessTo(String train, String fromTrack, String toTrack) {
		long start = System.currentTimeMillis();
		boolean granted = false;
		System.out.println("TRAIN "+train+" requests access to "+toTrack);
		while(!granted && System.currentTimeMillis()-start < TIMEOUT){
			synchronized(access){
				if(!isBlocked(toTrack)
						&& (access.get(toTrack)==null || access.get(toTrack).equals(train))){
					// assign track to this train
					access.put(toTrack, train);

					// check if switch is ok
					Optional<SwitchHandler> swtch = getSwitch(fromTrack, toTrack);
					if(shouldSwitch(getSwitch(fromTrack, toTrack), fromTrack, toTrack)){
						doSwitch(swtch.get().segment.id);
					} else {
						// set green signal
						greenSignal(getSignal(fromTrack));
						// now grant the access
						granted = true;
					}
				} 
				
				// if not granted, wait until timeout
				if(!granted){
					try {
						long wait = TIMEOUT-System.currentTimeMillis()+start;
						if(wait > 0)
							access.wait(wait);
					} catch (InterruptedException e) {
					}
				}
			}
		}
		
		return granted;
	}

	// set the signal to green for 10 seconds
	private void greenSignal(Optional<SignalHandler> signal){
		if(signal.isPresent()){
			setSignal(signal.get().segment.id, Color.GREEN);
			scheduler.after(()-> setSignal(signal.get().segment.id,Color.YELLOW),10000);
			scheduler.after(()-> setSignal(signal.get().segment.id,Color.RED),15000);
		}
	}
	
	// checks whether the switch is in the right state to go from fromTrack to toTrack
	private boolean shouldSwitch(Optional<SwitchHandler> swtch, String fromTrack, String toTrack){
		if(!swtch.isPresent()){
			logger.debug("No switch between "+fromTrack+" and "+toTrack);
			return true;
		}
		SwitchHandler s = swtch.get();
		
		// check (and set) signal and switch
		boolean switchOK = true;
		if(s.isMerge()){
			// check if previous is fromTrack
			if(s.prev.getTrack().equals(fromTrack)){
				// if so, then alternate should be false
				if(s.toAlternate){
					switchOK = false;
				}
				// else alternate should be true
			} else if(!s.toAlternate){
				switchOK = false;
			}
		} else {
			// check if next is toTrack 
			if(s.next.getTrack().equals(toTrack)){
				// if so, then alternate should be false
				if(s.toAlternate){
					switchOK = false;
				}
				// else alternate should be true
			} else if(!s.toAlternate){
				switchOK = false;
			}
		}
		
		return !switchOK;
	}
	
	// check if any of the blocked segments is on this track
	private boolean isBlocked(String track){
		for(String s : blocked){
			if(tracks.getHandler(s).getTrack().equals(track)){
				return true;
			}
		}
		return false;
	}

	private Optional<SwitchHandler> getSwitch(String fromTrack, String toTrack){
		return tracks.filter(SwitchHandler.class)
				.filter(sh -> sh.prev.getTrack().equals(fromTrack)
								|| (sh.altPrev!=null 
									 && sh.altPrev.getTrack().equals(fromTrack)))
				.filter(sh -> sh.next.getTrack().equals(toTrack)
								|| (sh.altNext !=null
								     && sh.altNext.getTrack().equals(toTrack))).findFirst();
	}
	
	private Optional<SignalHandler> getSignal(String fromTrack){
		return tracks.filter(SignalHandler.class)
				.filter(sh -> sh.getTrack().equals(fromTrack))
				.findFirst();
	}
	
	private void releasePreviousTrack(String train, String track) {
		synchronized(access){
			// remove the previous track of the train currently at @track to release from access
			Optional<String> previous = access.entrySet().stream()
											.filter(e -> e.getKey().equals(train))
											.filter(e -> !e.getKey().equals(track))
											.map(e -> e.getKey()).findFirst();
			if(previous.isPresent()){
				access.remove(previous);
				access.notifyAll();
			}
		}
	}

	@Override
	public void registerTrain(String name, String rfid) {
		logger.info("Train " + name + " with rfid "+rfid+ " registered");
		trains.put(rfid, name);
	}

	@Override
	public void locatedTrainAt(String rfid, String segment) {
		String train = trains.get(rfid);
		if ( train == null)
			throw new IllegalArgumentException("Unknown train for rfid " + rfid);

		Locator handler = tracks.getHandler(Locator.class, segment);
		handler.locatedAt(train);
		
		releasePreviousTrack(train, tracks.getHandler(segment).getTrack());
	}

	@Override
	public void switched(String segment, boolean alternative) {
		Switch swtch = tracks.getHandler(Switch.class, segment);
		swtch.alternative(alternative);
		
		synchronized(access){
			access.notifyAll();
		}
	}

	@Override
	public void signal(String segment, Color color) {
		Signal signal = tracks.getHandler(Signal.class, segment);
		signal.setColor(color);
		
		synchronized(access){
			access.notifyAll();
		}
	}

	void observation(Observation o) {
		try {
			o.time = System.currentTimeMillis();
			synchronized(observations) {
				o.id = offset + observations.size();
				observations.add(o);
				observations.notifyAll();
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
		return blocked;
	}

	@Override
	public void blocked(String segment, String reason, boolean b) {
		synchronized(access){
			if(b){
				blocked.add(segment);
			} else {
				blocked.remove(segment);
			}
			access.notifyAll();
		}
		Observation o = new Observation();
		o.type = Type.BLOCKED;
		o.segment = segment;
		o.blocked = b;
		observation(o);
	}
}
