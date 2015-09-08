package osgi.enroute.trains.application;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import osgi.enroute.configurer.api.RequireConfigurerExtender;
import osgi.enroute.eventadminserversentevents.capabilities.RequireEventAdminServerSentEventsWebResource;
import osgi.enroute.google.angular.capabilities.RequireAngularWebResource;
import osgi.enroute.jsonrpc.api.JSONRPC;
import osgi.enroute.jsonrpc.api.RequireJsonrpcWebResource;
import osgi.enroute.trains.application.LayoutAdapter.Layout;
import osgi.enroute.trains.cloud.api.Segment;
import osgi.enroute.trains.cloud.api.TrackInfo;
import osgi.enroute.trains.track.util.Track;
import osgi.enroute.trains.track.util.Track.SegmentHandler;
import osgi.enroute.twitter.bootstrap.capabilities.RequireBootstrapWebResource;
import osgi.enroute.webserver.capabilities.RequireWebServerExtender;

@RequireAngularWebResource(resource = { "angular.js", "angular-resource.js", "angular-route.js" }, priority = 1000)
@RequireBootstrapWebResource(resource = "css/bootstrap.css")
@RequireWebServerExtender
@RequireConfigurerExtender
@RequireEventAdminServerSentEventsWebResource
@RequireJsonrpcWebResource
@Component(name = "osgi.enroute.trains", property = JSONRPC.ENDPOINT + "=trains")
public class TrainsApplication implements JSONRPC {

	@Reference
	private TrackInfo ti;
	private Track<Layout> track;
	private Map<String,SegmentPosition> positions;

	@Activate
	void activate() throws Exception {
		try {
			track = new Track<>(ti.getSegments().values(), new LayoutAdapter());
			track.getRoot().get().layout(0, 0, null);
			for ( SegmentHandler<Layout> sh : track.getHandlers()) {
				sh.get().adjustWidth();
			}
			positions = Collections.unmodifiableMap(
					track.getHandlers().stream().map(sh -> sh.get().getPosition()).collect(Collectors.toMap( p -> p.segment.id, p->p)));
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Map<String, Segment> getSegments() {
		security();
		return ti.getSegments();
	}

	public Map<String,SegmentPosition> getPositions() {
		return positions;
	}

	private void security() {
		// TODO Auto-generated method stub

	}

	@Override
	public Object getDescriptor() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
