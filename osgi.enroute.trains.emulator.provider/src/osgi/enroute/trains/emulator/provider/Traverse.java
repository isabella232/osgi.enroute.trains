package osgi.enroute.trains.emulator.provider;

import java.io.Closeable;

import org.osgi.framework.BundleContext;

public interface Traverse extends Closeable {
	Traverse next(String rfid);

	Traverse prev(String rfid);

	void register(BundleContext context);

	int l();

	default boolean isBlocked() {
		return false;
	}
}
