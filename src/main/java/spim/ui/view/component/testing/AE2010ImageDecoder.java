package spim.ui.view.component.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import mmcorej.TaggedImage;

import static org.micromanager.acquisition.internal.TaggedImageQueue.POISON;
import static org.junit.Assert.*;


@org.junit.Ignore
public class AE2010ImageDecoder {
	public static List<TaggedImage> collectImages(BlockingQueue<TaggedImage> q)
			throws InterruptedException
	{
		List<TaggedImage> packets = new ArrayList<>();

		for (;;) {
			TaggedImage tim = q.poll(1, TimeUnit.SECONDS);
			assertNotNull(tim);
			if (tim == POISON) {
				assertTrue("images should not remain in queue after POISON",
						q.isEmpty());
				break;
			}

			packets.add(tim);
		}
		return packets;
	}
}