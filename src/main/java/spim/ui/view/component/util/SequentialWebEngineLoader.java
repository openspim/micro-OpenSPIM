package spim.ui.view.component.util;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;

import java.util.concurrent.Semaphore;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: August 2021
 *
 * SequentialWebEngineLoader is necessary since each WebEngine sharing a single WebLoader and the WebLoader completes
 * multiple tasks asynchronously. It gives a confusion to the callers.
 */
public class SequentialWebEngineLoader {
	private static Semaphore mutex = new Semaphore(1);

	static class EngineWorker implements Runnable
	{
		private WebEngine engine;
		private String content;

		EngineWorker(WebEngine engine, String content, ChangeListener<Worker.State> observer) {
			this.engine = engine;
			this.content = content;

			engine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
				@Override
				public void changed( ObservableValue<? extends Worker.State> observableValue, Worker.State oldValue, Worker.State newValue ) {
					if ( newValue == Worker.State.SUCCEEDED ) {
						engine.getLoadWorker().stateProperty().removeListener( this );
						observer.changed( observableValue, oldValue, newValue );
						mutex.release();
					}
				}
			});
		}

		@Override
		public void run() {
			try {
				mutex.acquire();
				Platform.runLater(() -> engine.load( content ));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void load( WebEngine engine, String content, ChangeListener<Worker.State> observer )
	{
		new Thread(new EngineWorker(engine, content, observer)).start();
	}
}
