package osgi.enroute.trains.realworld.provider;

import java.util.Dictionary;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import osgi.enroute.trains.track.util.Track.SegmentHandler;
import osgi.enroute.trains.train.api.TrainController;

public class TrainControllerImpl implements TrainController {
	private double SPEED_CONSTANT = 0.1;

	private double distance;
	private int desiredSpeed;
	private int actualSpeed;
	private Traverse current;
	private String rfid;

	private ServiceRegistration<TrainController> registration;

	public TrainControllerImpl(String rfid, SegmentHandler<Traverse> start) {
		this.rfid = rfid;
		this.current = start.get();
	}

	public Dictionary<String, ?> getProperties() {
		return null;
	}

	@Override
	public void move(int directionAndSpeed) {
		this.desiredSpeed = directionAndSpeed;
	}

	@Override
	public void light(boolean on) {
		System.out.println("Light " + rfid + " " + on);
	}

	void tick() {
		try {
			if (current == null)
				return;

			actualSpeed = desiredSpeed + (desiredSpeed - actualSpeed + 2) / 4;
			distance += SPEED_CONSTANT * actualSpeed;

			double l = current.l();
			if (distance > l) {
				current = current.next(rfid);
				distance -= l;
			} else if (distance < 0) {
				current = current.prev(rfid);
				distance = 0;
			} else {
				//System.out.println("->" + current + " " + l + " " + distance);
				
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void close() {
		registration.unregister();
	}

	public void register(BundleContext context) {
		registration = context.registerService(TrainController.class, this, null);
	}
}
