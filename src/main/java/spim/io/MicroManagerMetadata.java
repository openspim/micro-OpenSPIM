package spim.io;

import org.janelia.saalfeldlab.n5.metadata.N5Metadata;
import org.micromanager.PropertyMap;

import java.io.IOException;

/**
 * Description: MicroManagerMetadata interface for N5 storage.
 *
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2021
 */
public interface MicroManagerMetadata<T extends N5Metadata> {
	void writeMetadata(T var1, PropertyMap var2) throws IOException;

	T readMetadata(PropertyMap var1) throws IOException;
}