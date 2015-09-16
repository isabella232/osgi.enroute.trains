package osgi.enroute.trains.train.manager.example.provider;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import osgi.enroute.trains.cloud.api.Observation;
import osgi.enroute.trains.cloud.api.TrackForTrain;
import osgi.enroute.trains.track.util.Tracks;
import osgi.enroute.trains.track.util.Tracks.SegmentHandler;
import osgi.enroute.trains.train.api.TrainController;

/**
 * 
 */
@Component(name = "osgi.enroute.trains.train.manager.example",
	immediate=true,
	service=Object.class,
	property={"osgi.command.scope=trains","osgi.command.function=move"})
public class ExampleTrainManagerImpl {

	private TrackForTrain trackManager;
	private TrainController train;
	private String name;
	
	private Tracks<Object> tracks;
	
	private volatile boolean active = false;
	private Thread mgmtThread;
	
	@Activate
	public void activate() throws Exception {
		// TODO where does the TrainManager get his name from?
		//name = "T1";
		name = "rfid1234";
		
		// register train with Track Manager
		trackManager.registerTrain(name, "Train");
		// create Track
		tracks = new Tracks<Object>(trackManager.getSegments().values(), new TrainManagerFactory());

		active = true;
		mgmtThread = new Thread(new TrainMgmtLoop());
		mgmtThread.start();
	}
	
	@Deactivate
	public void deactivate(){
		active = false;
		try {
			mgmtThread.join();
		} catch (InterruptedException e) {
		}
		// stop when deactivated
		train.move(0);
		// turn lights off
		train.light(false);
	}
	
	@Reference
	public void setTrainController(TrainController t){
		this.train = t;
	}

	@Reference
	public void setTrackManager(TrackForTrain t){
		this.trackManager = t;
	}

	private class TrainMgmtLoop implements Runnable {

		private String currentAssignment = null;
		private String currentLocation = null;
		private LinkedList<SegmentHandler> route = null;
		
		@Override
		public void run() {
			// turn the train light on 
			train.light(true);
			// start moving on activation
			train.move(50);

			// last observation id
			long lastObservation = -1;
			
			while(active){
				// mgmt loop
				List<Observation> observations = trackManager.getRecentObservations(lastObservation);
				for(Observation o : observations){
					lastObservation = o.id;
					
					tracks.event(o);
					
					if(!name.equals(o.train)){
						continue;
					}
					
					switch(o.type){
					case ASSIGNMENT:
						currentAssignment = o.assignment;
						// new assignment, plan and follow the route
						System.out.println(name+ " gets new assignment "+o.assignment);
						planRoute();
						followRoute();
						break;
					case LOCATED:
						currentLocation = o.segment;
						
						// if first time location found and already an assignment is set,
						// plan route
						if(currentLocation == null && currentAssignment!=null){
							planRoute();
						}
						
						// stop current assignment reached (no assignment = assignment reached)
						if(assignmentReached()){
							train.move(0);
						} else {
							followRoute();
						}
						break;
					}
				}
			}
		}
		
		private void planRoute(){
			if(currentLocation==null)
				return;
		
			if(currentAssignment==null)
				return;
			
			// plan the route
			SegmentHandler src = tracks.getHandler(currentLocation);
			SegmentHandler dest = tracks.getHandler(currentAssignment);
			route = src.findForward(dest);
		}
		
		private void followRoute(){
			if(route==null)
				return;
			
			// update the remaining part of the current route
			while(route.size() > 0 && !route.getFirst().segment.id.equals(currentLocation)){
				SegmentHandler sh = route.removeFirst();
			}
			
			// figure out where to go to next
			String fromTrack = route.removeFirst().getTrack();
			
			// check if we have to go to a new track before we have a new Locator
			Optional<SegmentHandler> nextLocator = route.stream().filter(sh -> sh.isLocator()).findFirst();
			if(!nextLocator.isPresent()){
				// no locator to go to, stop now
				train.move(0);
				return;
			}
			
			String toTrack = nextLocator.get().getTrack();
			
			// check if we have to go to other track, in that case request access
			if(!fromTrack.equals(toTrack)){
				// stop and request access
				train.move(0);
				
				boolean access = false;
				// simply keep on trying until access is given
				while(!access && active){
					System.out.println(name+" requests access from track "+fromTrack+" to "+toTrack);
					access = trackManager.requestAccessTo(name, fromTrack, toTrack);
				}
			} 
			
			// just go forward
			train.move(50);
		}
		
		private boolean assignmentReached(){
			if(currentAssignment==null || currentAssignment.equals(currentLocation)){
				if(currentAssignment!=null){
					System.out.println(name+" has reached assignment "+currentAssignment);
				} else {
					System.out.println(name+" is waiting for an assignment");
				}
				return true;
			}
			return false;
		}
	}
	
	// make train move from gogo shell command
	public void move(int directionAndSpeed){
		this.train.move(directionAndSpeed);
	}

}
