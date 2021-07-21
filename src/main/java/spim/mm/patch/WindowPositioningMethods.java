package spim.mm.patch;

import com.google.common.base.Preconditions;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.propertymap.MutablePropertyMapView;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Description: Overriding class of existing WindowPosition.
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2020
 */
public class WindowPositioningMethods
{
	private static final Map<Window, WindowPositioningMethods.WindowCascade> windowCascades_ = new WeakHashMap();

	public static void setUpBoundsMemory(java.awt.Window window, java.lang.Class positioningClass, java.lang.String positioningKey) {
		WindowPositioningMethods.GeometrySaver.createAndRestoreBounds(window, positioningClass, positioningKey);
	}

	private static class WindowCascade {
		private final List< WeakReference<Window> > windows_;
		private Point resetCursor_;

		private WindowCascade() {
			this.windows_ = new LinkedList();
		}

		void addWindow(Window window) {
			Window previous = this.getNewestVisibleWindow();
			this.windows_.add(new WeakReference(window));
			if (previous != null) {
				Point location = previous.getLocation();
				location.translate(20, 24);
				window.setLocation(location);
			}

			WindowPositioning.fitWindowInScreen(window);
		}

		Window getNewestVisibleWindow() {
			ListIterator iterator = this.windows_.listIterator(this.windows_.size());

			while(iterator.hasPrevious()) {
				Window window = (Window)((WeakReference)iterator.previous()).get();
				if (window == null) {
					iterator.remove();
				} else if (window.isVisible()) {
					return window;
				}
			}

			return null;
		}

		Window getOldestVisibleWindow() {
			ListIterator iterator = this.windows_.listIterator();

			while(iterator.hasNext()) {
				Window window = (Window)((WeakReference)iterator.next()).get();
				if (window == null) {
					iterator.remove();
				} else if (window.isVisible()) {
					return window;
				}
			}

			return null;
		}

		void setResetCursor(Point cursor) {
			this.resetCursor_ = new Point(cursor);
		}

		Point getResetCursor() {
			return new Point(this.resetCursor_);
		}
	}

	private static class GeometrySaver implements ComponentListener
	{
		private final WindowPositioningMethods.GeometrySaver.Mode mode_;
		private final Class<?> positioningClass_;
		private final String positioningKey_;
		private final Window window_;

		static WindowPositioningMethods.GeometrySaver createAndRestoreBounds(Window window, Class<?> positioningClass, String positioningKey) {
			WindowPositioningMethods.GeometrySaver saver = new WindowPositioningMethods.GeometrySaver(window, positioningClass, positioningKey, WindowPositioningMethods.GeometrySaver.Mode.MEMORIZE_BOUNDS);
			saver.restoreGeometry();
			saver.attachToWindow();
			return saver;
		}

		private GeometrySaver(Window window, Class<?> positioningClass, String positioningKey, WindowPositioningMethods.GeometrySaver.Mode mode) {
			Preconditions.checkNotNull(window);
			this.window_ = window;
			this.positioningClass_ = positioningClass;
			this.positioningKey_ = positioningKey == null ? "" : positioningKey;
			this.mode_ = mode;
		}

		private void attachToWindow() {
			this.window_.addComponentListener(this);
		}

		private void saveGeometry() {
			Window window = this.window_;
			WindowPositioningMethods.WindowCascade cascade = (WindowPositioningMethods.WindowCascade)WindowPositioningMethods.windowCascades_.get(window);
			if (cascade != null) {
				Window oldest = cascade.getOldestVisibleWindow();
				if (oldest != null) {
					window = oldest;
				}
			}

			Rectangle bounds = window.getBounds();

			if(MMStudio.getInstance() == null) return;

			MutablePropertyMapView settings = MMStudio.getInstance().profile().getSettings(this.getClass());
			PropertyMap classPmap = settings.getPropertyMap(this.positioningClass_.getCanonicalName(), PropertyMaps.emptyPropertyMap());
			PropertyMap keyPmap = classPmap.getPropertyMap(this.positioningKey_, PropertyMaps.emptyPropertyMap());
			PropertyMap.Builder builder = keyPmap.copyBuilder();
			switch(this.mode_) {
				case MEMORIZE_BOUNDS:
					builder.putRectangle(WindowPositioningMethods.GeometrySaver.ProfileKey.WINDOW_BOUNDS.name(), bounds);
					break;
				case MEMORIZE_LOCATION:
					builder.putPoint(WindowPositioningMethods.GeometrySaver.ProfileKey.WINDOW_LOCATION.name(), bounds.getLocation());
					break;
				default:
					throw new AssertionError(this.mode_);
			}

			keyPmap = builder.build();
			classPmap = classPmap.copyBuilder().putPropertyMap(this.positioningKey_, keyPmap).build();
			settings.putPropertyMap(this.positioningClass_.getCanonicalName(), classPmap);
		}

		private void restoreGeometry() {
			PropertyMap pmap = MMStudio.getInstance().profile().getSettings(this.getClass()).getPropertyMap(this.positioningClass_.getCanonicalName(), PropertyMaps.emptyPropertyMap()).getPropertyMap(this.positioningKey_, PropertyMaps.emptyPropertyMap());
			switch(this.mode_) {
				case MEMORIZE_BOUNDS:
					this.window_.setBounds(pmap.getRectangle(WindowPositioningMethods.GeometrySaver.ProfileKey.WINDOW_BOUNDS.name(), this.window_.getBounds()));
					break;
				case MEMORIZE_LOCATION:
					this.window_.setLocation(pmap.getPoint(WindowPositioningMethods.GeometrySaver.ProfileKey.WINDOW_LOCATION.name(), this.window_.getLocation()));
					break;
				default:
					throw new AssertionError(this.mode_);
			}

			WindowPositioning.fitWindowInScreen(this.window_);
		}

		public void componentResized( ComponentEvent e) {
			this.saveGeometry();
		}

		public void componentMoved(ComponentEvent e) {
			this.saveGeometry();
		}

		public void componentShown(ComponentEvent e) {
			this.saveGeometry();
		}

		public void componentHidden(ComponentEvent e) {
			this.saveGeometry();
		}

		private enum ProfileKey {
			WINDOW_LOCATION,
			WINDOW_BOUNDS;

			ProfileKey() {
			}
		}

		private enum Mode {
			MEMORIZE_BOUNDS,
			MEMORIZE_LOCATION;

			Mode() {
			}
		}
	}
}
