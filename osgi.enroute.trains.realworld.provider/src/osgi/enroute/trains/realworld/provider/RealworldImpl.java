package osgi.enroute.trains.realworld.provider;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import osgi.enroute.dto.api.DTOs;
import osgi.enroute.scheduler.api.Scheduler;
import osgi.enroute.trains.cloud.api.Observation;
import osgi.enroute.trains.cloud.api.TrackForSegment;
import osgi.enroute.trains.cloud.api.TrackForTrain;
import osgi.enroute.trains.event.util.EventToObservation;
import osgi.enroute.trains.track.util.Track;
import osgi.enroute.trains.track.util.Track.SegmentHandler;

/**
 * 
 */
@Component(name = "osgi.enroute.trains.realworld", immediate = true, property=EventConstants.EVENT_TOPIC+"="+Observation.TOPIC)
public class RealworldImpl implements EventHandler {

	@Reference
	private TrackForSegment trackForSegment;
	@Reference
	private TrackForTrain trackForTrain;
	@Reference
	private Scheduler scheduler;
	@Reference
	private DTOs dtos;

	private List<TrainControllerImpl> trainControllers = new ArrayList<>();
	private Track<Traverse> track;
	private Closeable trainTick;
	private Closeable poll;
	private List<String> trains;

	@ObjectClassDefinition
	@interface Config {
		String[] trains() default {};
	}
	@Activate
	void activate(Config config, BundleContext context) throws Exception {
		trains = Arrays.asList(config.trains());

		track = new Track<Traverse>(trackForTrain.getSegments().values(), new RealWorldFactory(trackForSegment));

		track.getHandlers().forEach(sh -> sh.get().register(context));

		for (String rfid : trains) {
			TrainControllerImpl trainControllerImpl = new TrainControllerImpl(rfid,track.getRoot());
			trainControllers.add(trainControllerImpl);
			trainControllerImpl.register(context);
		}

		trainTick = scheduler.schedule(this::tick, 100, 50);
	}

	@Deactivate
	void deactivate() throws IOException {
		this.trainTick.close();
		this.poll.close();
		for (TrainControllerImpl tci : trainControllers) {
			tci.close();
		}
		for ( SegmentHandler<Traverse> sh : track.getHandlers())
			sh.get().close();
	}

	void tick() throws Exception {
		for (TrainControllerImpl tc : trainControllers)
			tc.tick();
	}

	
	@Override
	public void handleEvent(Event event) {
		try {
			Observation observation = EventToObservation.eventToObservation(event, dtos);
			track.event(observation);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
