package spim.io;

import net.imglib2.Interval;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ScaleAndTranslation;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.metadata.*;
import org.micromanager.PropertyMap;
import org.micromanager.data.internal.PixelType;
import org.micromanager.internal.propertymap.DefaultPropertyMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: June 2021
 */
public class N5MicroManagerMetadata extends AbstractN5Metadata<N5MicroManagerMetadata> implements MicroManagerMetadata<N5MicroManagerMetadata>, PhysicalMetadata {
	public static final String titleKey = "title";
	public static final String fpsKey = "fps";
	public static final String frameIntervalKey = "frameInterval";
	public static final String pixelWidthKey = "pixelWidth";
	public static final String pixelHeightKey = "pixelHeight";
	public static final String pixelDepthKey = "pixelDepth";
	public static final String pixelUnitKey = "pixelUnit";
	public static final String xOriginKey = "xOrigin";
	public static final String yOriginKey = "yOrigin";
	public static final String zOriginKey = "zOrigin";
	public static final String numChannelsKey = "numChannels";
	public static final String numSlicesKey = "numSlices";
	public static final String numFramesKey = "numFrames";
	public static final String typeKey = "ImagePlusType";
	public static final String imagePropertiesKey = "imageProperties";
	public static final String downsamplingFactorsKey = "downsamplingFactors";
	private String name;
	private double fps;
	private double frameInterval;
	private double pixelWidth;
	private double pixelHeight;
	private double pixelDepth;
	private double xOrigin;
	private double yOrigin;
	private double zOrigin;
	private int numChannels;
	private int numSlices;
	private int numFrames;
	private int type;
	private String unit;
	private Map<String, Object> properties;
	private HashMap<String, Class<?>> keysToTypes;

	public N5MicroManagerMetadata(String path) {
		this(path, "pixel");
	}

	public N5MicroManagerMetadata(String path, DatasetAttributes attributes) {
		super(path, attributes);
	}

	public N5MicroManagerMetadata(String path, String unit, double... resolution) {
		this(path, unit, (DatasetAttributes)null, resolution);
	}

	public N5MicroManagerMetadata(String path, String unit, DatasetAttributes attributes, double... resolution) {
		super(path, attributes);
		this.unit = unit;
		this.pixelWidth = 1.0D;
		this.pixelHeight = 1.0D;
		this.pixelDepth = 1.0D;
		if (resolution.length > 0) {
			this.pixelWidth = resolution[0];
		}

		if (resolution.length > 1) {
			this.pixelHeight = resolution[1];
		}

		if (resolution.length > 2) {
			this.pixelDepth = resolution[2];
		}

		this.keysToTypes = new HashMap();
		this.keysToTypes.put("title", String.class);
		this.keysToTypes.put("pixelWidth", Double.class);
		this.keysToTypes.put("pixelHeight", Double.class);
		this.keysToTypes.put("pixelDepth", Double.class);
		this.keysToTypes.put("pixelUnit", String.class);
		this.keysToTypes.put("xOrigin", Double.class);
		this.keysToTypes.put("yOrigin", Double.class);
		this.keysToTypes.put("zOrigin", Double.class);
		this.keysToTypes.put("fps", Double.class);
		this.keysToTypes.put("numChannels", Integer.class);
		this.keysToTypes.put("numSlices", Integer.class);
		this.keysToTypes.put("numFrames", Integer.class);
		this.keysToTypes.put("ImagePlusType", Integer.class);
		AbstractN5Metadata.addDatasetAttributeKeys(this.keysToTypes);
	}

	public void crop(Interval cropInterval) {
		int i = 2;
		if (this.numChannels > 1) {
			this.numChannels = (int)cropInterval.dimension(i++);
		}

		if (this.numSlices > 1) {
			this.numSlices = (int)cropInterval.dimension(i++);
		}

		if (this.numFrames > 1) {
			this.numFrames = (int)cropInterval.dimension(i++);
		}

	}

	public static double[] getPixelSpacing(N5Reader n5, String dataset) throws IOException {
		double rx = (Double)n5.getAttribute(dataset, "pixelWidth", Double.TYPE);
		double ry = (Double)n5.getAttribute(dataset, "pixelHeight", Double.TYPE);
		double rz = (Double)n5.getAttribute(dataset, "pixelDepth", Double.TYPE);
		return new double[]{rx, ry, rz};
	}

	public int getType() {
		return this.type;
	}

	@Override
	public void writeMetadata(N5MicroManagerMetadata t, PropertyMap map) {

		PropertyMap.Builder pb = new DefaultPropertyMap.Builder();

		pb.putString("Prefix", t.name);
		pb.putDouble("Width", t.pixelWidth);
		pb.putDouble("Height", t.pixelHeight);

		pb.putString("Unit", t.unit);
		pb.putInteger("Channels", t.numChannels);
		pb.putInteger("Slices", t.numSlices);
		pb.putInteger("Frames", t.numFrames);

		pb.putDouble("FPS", t.fps);
		pb.putDouble("FrameInterval", t.frameInterval);
		pb.putDouble("xOrigin", t.xOrigin);
		pb.putDouble("yOrigin", t.yOrigin);
		pb.putDouble("zOrigin", t.zOrigin);


		switch (t.type) {
			case 0:
				pb.putEnumAsString("PixelType", PixelType.GRAY8);
				break;
			case 1:
				pb.putEnumAsString("PixelType", PixelType.GRAY16);
				break;
			case 4:
				pb.putEnumAsString("PixelType", PixelType.RGB32);
				break;
		}

		if (t.properties != null) {
			Iterator iter = t.properties.keySet().iterator();

			while(iter.hasNext()) {
				String k = (String)iter.next();

				try {
					pb.putString(k, t.properties.get(k).toString());
				} catch (Exception var8) {
				}
			}
		}
	}

	@Override
	public N5MicroManagerMetadata readMetadata(PropertyMap map) {
		AffineTransform3D xfm = new AffineTransform3D();
		N5MicroManagerMetadata t = this;

		PropertyMap pm = map;

		t.name = pm.getString("Prefix", "Untitled");
		t.pixelWidth = pm.getInteger("Width", 0);
		t.pixelHeight = pm.getInteger("Height", 0);
		t.pixelDepth = pm.getDouble("z-step_um", 1);

		t.unit = pm.getString("Unit", "pixel");
		t.numChannels = pm.getInteger("Channels", 1);
		t.numSlices = pm.getInteger("Slices", 1);
		t.numFrames = pm.getInteger("Frames", 1);

		t.fps = pm.getDouble("FPS", 1);
		t.frameInterval = pm.getDouble("FrameInterval", 1);
		t.xOrigin = pm.getDouble("xOrigin", 0);
		t.yOrigin = pm.getDouble("yOrigin", 0);
		t.zOrigin = pm.getDouble("zOrigin", 0);

		PixelType type = pm.getStringAsEnum("PixelType", PixelType.class, null);

		if(type != null)
			switch (type) {
				case GRAY8: t.type = 0; // ByteProcessor
					t.pixelDepth = 1;
					break;
				case GRAY16:
					t.type = 1; // ShortProcessor
					t.pixelDepth = 2;
					break;
				case RGB32: t.type = 4; // ColorProcessor
					t.pixelDepth = 4;
					break;
			}
		else {
			t.type = 1;
			t.pixelDepth = 2;
		}

		xfm.set(t.pixelWidth, 0, 0);
		xfm.set(t.pixelHeight, 1, 1);
		xfm.set(t.pixelDepth, 2, 2);
		t.xOrigin = 0;
		t.yOrigin = 0;
		t.zOrigin = 0;

		PropertyMap props = pm;
		if (props != null) {
			t.properties = new HashMap();
			Iterator iter = props.keySet().iterator();

			while(iter.hasNext()) {
				String k = iter.next().toString();

				try {
					t.properties.put(k, props.getValueAsString(k, ""));
				} catch (Exception var9) {
				}
			}
		}

		return t;
	}


	@Override
	public HashMap<String, Class<?>> keysToTypes() {
		return this.keysToTypes;
	}

	@Override
	public N5MicroManagerMetadata parseMetadata(Map<String, Object> metaMap) throws Exception {
		if (!this.check(metaMap)) {
			return null;
		} else {
			String dataset = (String)metaMap.get("dataset");
			DatasetAttributes attributes = N5MetadataParser.parseAttributes(metaMap);
			if (attributes == null) {
				return null;
			} else {
				N5MicroManagerMetadata meta = new N5MicroManagerMetadata(dataset, attributes);
				meta.name = (String)metaMap.get("title");
				meta.pixelWidth = (Double)metaMap.get("pixelWidth");
				meta.pixelHeight = (Double)metaMap.get("pixelHeight");
				meta.pixelDepth = (Double)metaMap.get("pixelDepth");
				meta.unit = (String)metaMap.get("pixelUnit");
				meta.xOrigin = (Double)metaMap.get("xOrigin");
				meta.yOrigin = (Double)metaMap.get("yOrigin");
				meta.zOrigin = (Double)metaMap.get("zOrigin");
				meta.numChannels = (Integer)metaMap.get("numChannels");
				meta.numSlices = (Integer)metaMap.get("numSlices");
				meta.numFrames = (Integer)metaMap.get("numFrames");
				meta.fps = (Double)metaMap.get("fps");
				meta.frameInterval = (Double)metaMap.get("fps");
				if (metaMap.containsKey("ImagePlusType")) {
					meta.type = (Integer)metaMap.get("ImagePlusType");
				}

				return meta;
			}
		}
	}

	@Override
	public void writeMetadata(N5MicroManagerMetadata t, N5Writer n5, String dataset) throws Exception {
		if (!n5.datasetExists(dataset)) {
			throw new Exception("Can't write into " + dataset + ".  Must be a dataset.");
		} else {
			HashMap<String, Object> attrs = new HashMap();
			attrs.put("title", t.name);
			attrs.put("fps", t.fps);
			attrs.put("frameInterval", t.frameInterval);
			attrs.put("pixelWidth", t.pixelWidth);
			attrs.put("pixelHeight", t.pixelHeight);
			attrs.put("pixelDepth", t.pixelDepth);
			attrs.put("pixelUnit", t.unit);
			attrs.put("xOrigin", t.xOrigin);
			attrs.put("yOrigin", t.yOrigin);
			attrs.put("zOrigin", t.zOrigin);
			attrs.put("numChannels", t.numChannels);
			attrs.put("numSlices", t.numSlices);
			attrs.put("numFrames", t.numFrames);
			attrs.put("ImagePlusType", t.type);
			if (t.properties != null) {
				Iterator var5 = t.properties.keySet().iterator();

				while(var5.hasNext()) {
					Object k = var5.next();

					try {
						attrs.put(k.toString(), t.properties.get(k).toString());
					} catch (Exception var8) {
					}
				}
			}

			n5.setAttributes(dataset, attrs);
		}
	}

	@Override
	public AffineGet physicalTransform() {
		int nd = this.numSlices > 1 ? 3 : 2;
		double[] spacing = new double[nd];
		double[] offset = new double[nd];
		spacing[0] = this.pixelWidth;
		spacing[1] = this.pixelHeight;
		if (this.numSlices > 1) {
			spacing[2] = this.pixelDepth;
		}

		offset[0] = this.xOrigin;
		offset[1] = this.yOrigin;
		if (this.numSlices > 1) {
			offset[2] = this.zOrigin;
		}

		return new ScaleAndTranslation(spacing, offset);
	}

	@Override
	public String[] units() {
		int nd = this.numSlices > 1 ? 3 : 2;
		return (String[])((String[]) Stream.generate(() -> {
			return this.unit;
		}).limit((long)nd).toArray());
	}
}
