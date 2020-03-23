package spim.mm;

import mmcorej.TaggedImage;

import java.util.List;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: March 2020
 */
public interface LiveListener
{
	void liveImgReceived( List< TaggedImage > images);

	void liveStarted();

	void liveStopped();
}
