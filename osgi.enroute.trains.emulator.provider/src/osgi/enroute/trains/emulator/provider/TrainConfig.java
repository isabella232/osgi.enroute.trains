package osgi.enroute.trains.emulator.provider;

import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition
public @interface TrainConfig {
	
	String name();
	
	String rfid();
	
}
