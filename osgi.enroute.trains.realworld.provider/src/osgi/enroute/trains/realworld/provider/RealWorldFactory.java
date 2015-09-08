package osgi.enroute.trains.realworld.provider;

import java.io.IOException;
import java.util.Hashtable;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import osgi.enroute.trains.cloud.api.Color;
import osgi.enroute.trains.cloud.api.Segment;
import osgi.enroute.trains.cloud.api.TrackForSegment;
import osgi.enroute.trains.controller.api.SegmentController;
import osgi.enroute.trains.controller.api.SignalSegmentController;
import osgi.enroute.trains.controller.api.SwitchSegmentController;
import osgi.enroute.trains.track.util.SegmentFactoryAdapter;
import osgi.enroute.trains.track.util.Track.LocatorHandler;
import osgi.enroute.trains.track.util.Track.SegmentHandler;
import osgi.enroute.trains.track.util.Track.SignalHandler;
import osgi.enroute.trains.track.util.Track.SwitchHandler;

public class RealWorldFactory extends SegmentFactoryAdapter<Traverse> {
	final TrackForSegment owner;

	class Block extends SegmentHandler<Traverse>implements Traverse {

		public Block(Segment segment) {
			super(segment);
		}

		@Override
		public Traverse next(String rfid) {
			if (next != null)
				return next.get();
			return this;
		}

		@Override
		public Traverse prev(String rfid) {
			if (prev != null)
				return prev.get();
			return this;
		}

		@Override
		public void register(BundleContext context) {
		}

		@Override
		public void close() throws IOException {
		}

		@Override
		public int l() {
			return length();
		}

		public boolean isBlocked() {
			return true;
		}
	}

	class PassThru extends SegmentHandler<Traverse>implements Traverse {

		public PassThru(Segment segment) {
			super(segment);
		}

		@Override
		public Traverse next(String rfid) {
			return next.get();
		}

		@Override
		public Traverse prev(String rfid) {
			return prev.get();
		}

		@Override
		public void register(BundleContext context) {
		}

		@Override
		public void close() throws IOException {
		}
		@Override
		public int l() {
			return segment.length;
		}

	}

	class Switch extends SwitchHandler<Traverse>implements Traverse, SwitchSegmentController {

		private ServiceRegistration<SwitchSegmentController> registration;

		public Switch(Segment segment) {
			super(segment);
		}

		@Override
		public Traverse next(String rfid) {
			if (altNext != null && toAlternate)
				return altNext.get();
			else
				return next.get();
		}

		@Override
		public Traverse prev(String rfid) {
			// TODO is this right?
			if (toAlternate)
				return altPrev.get();
			else
				return prev.get();
		}

		@Override
		public void register(BundleContext context) {
			Hashtable<String, Integer> map = new Hashtable<>();
			map.put(SegmentController.CONTROLLER_ID, segment.controller);
			registration = context.registerService(SwitchSegmentController.class, this, map);
		}

		@Override
		public void swtch(boolean alternative) {
			toAlternate = alternative;
		}

		@Override
		public boolean getSwitch() {
			return toAlternate;
		}

		@Override
		public void close() throws IOException {
			registration.unregister();
		}
		@Override
		public int l() {
			return length();
		}

	}

	class Signal extends SignalHandler<Traverse>implements Traverse, SignalSegmentController {
		private ServiceRegistration<SignalSegmentController> registration;

		public Signal(Segment segment) {
			super(segment);
		}

		@Override
		public Traverse next(String rfid) {
			return next.get();
		}

		@Override
		public Traverse prev(String rfid) {
			return prev.get();
		}

		@Override
		public void register(BundleContext context) {
			Hashtable<String, Integer> map = new Hashtable<>();
			map.put(SegmentController.CONTROLLER_ID, segment.controller);
			registration = context.registerService(SignalSegmentController.class, this, map);
		}

		@Override
		public void signal(Color color) {

		}

		@Override
		public Color getSignal() {
			return color;
		}

		@Override
		public void close() throws IOException {
			registration.unregister();
		}
		@Override
		public int l() {
			return length();
		}

		public boolean isBlocked() {
			return color == Color.RED;
		}
	}

	class Locator extends LocatorHandler<Traverse>implements Traverse {

		public Locator(Segment segment) {
			super(segment);
		}

		@Override
		public Traverse next(String rfid) {
			owner.locatedTrainAt(rfid, segment.id);
			return next.get();
		}

		@Override
		public Traverse prev(String rfid) {
			owner.locatedTrainAt(rfid, segment.id);
			return prev.get();
		}

		@Override
		public void register(BundleContext context) {
		}

		@Override
		public void close() throws IOException {
		}
		@Override
		public int l() {
			return length();
		}

	}
	
	public RealWorldFactory(TrackForSegment owner) {
		this.owner = owner;
	}

	@Override
	public Block block(Segment segment) {
		return new Block(segment);
	}

	@Override
	public PassThru curve(Segment segment) {
		return new PassThru(segment);
	}

	@Override
	public PassThru straight(Segment segment) {
		return new PassThru(segment);
	}

	@Override
	public Locator locator(Segment segment) {
		return new Locator(segment);
	}

	@Override
	public Signal signal(Segment segment) {
		return new Signal(segment);
	}

	@Override
	public Switch swtch(Segment segment) {
		return new Switch(segment);
	}

}
