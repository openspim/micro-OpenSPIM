package spim.model.event;

import javafx.event.Event;
import javafx.event.EventType;

/**
 * Description: All the control events for event driven execution.
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: April 2019
 */
public class ControlEvent extends Event
{
	public enum ControlEventType
	{

	}

	public static final EventType<ControlEvent> ANY = new EventType<>( "CONTROL" );

	public static final EventType<ControlEvent> STAGE = new EventType<>( ANY, "STAGE" );

	public static final EventType<ControlEvent> LASER = new EventType<>( ANY, "LASER" );

	public static final EventType<ControlEvent> CAMERA = new EventType<>( ANY, "CAMERA" );

	public static final EventType<ControlEvent> SHUTTER = new EventType<>( ANY, "SHUTTER" );

	public static final EventType<ControlEvent> MM = new EventType<>( ANY, "MM" );

	// Stage specific events
	public static final EventType<ControlEvent> STAGE_MOVE = new EventType<>( STAGE, "STAGE_MOVE" );

	// Laser specific events
	public static final EventType<ControlEvent> LASER_ON = new EventType<>( LASER, "LASER_ON" );
	public static final EventType<ControlEvent> LASER_OFF = new EventType<>( LASER, "LASER_OFF" );

	// Camera specific events
	public static final EventType<ControlEvent> CAMERA_ACQUISITION_START = new EventType<>( CAMERA, "CAMERA_ACQUISITION_START" );
	public static final EventType<ControlEvent> CAMERA_ACQUISITION_STOP = new EventType<>( CAMERA, "CAMERA_ACQUISITION_STOP" );

	// Shutter specific events
	public static final EventType<ControlEvent> SHUTTER_OPEN = new EventType<>( SHUTTER, "SHUTTER_OPEN" );
	public static final EventType<ControlEvent> SHUTTER_CLOSE = new EventType<>( SHUTTER, "SHUTTER_CLOSE" );

	// MM Start events
	public static final EventType<ControlEvent> MM_OPEN = new EventType<>( MM, "MM_OPEN" );

	final private EventType<ControlEvent> eventType;
	final private Object[] param;

	public ControlEvent(EventType<ControlEvent> eventType, Object... param) {
		super(eventType);
		this.eventType = eventType;
		this.param = param;
	}

	public ControlEvent(EventType<ControlEvent> eventType) {
		this(eventType, null);
	}

	public static <T extends ControlEvent> ControlEventType getControlEventType( EventType<T> type )
	{
		return ControlEventType.valueOf( type.getName() );
	}

	@Override
	public EventType<ControlEvent> getEventType() { return eventType; }

	public Object[] getParam() { return param; }
 }
