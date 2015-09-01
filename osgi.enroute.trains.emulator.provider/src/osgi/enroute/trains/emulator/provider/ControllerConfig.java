package osgi.enroute.trains.emulator.provider;

import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition
public @interface ControllerConfig {

	int controller_id();
	
}
