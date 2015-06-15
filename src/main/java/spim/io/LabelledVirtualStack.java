package spim.io;

import java.awt.image.ColorModel;
import java.util.Arrays;

import org.micromanager.utils.ReportingUtils;

import ij.VirtualStack;

public class LabelledVirtualStack extends VirtualStack {
	private String[] labels;
	private String TWO_HUNDRED_SPACES = "                                                                                                                                                                                                        ";

	public LabelledVirtualStack() {
		super();
	}

	public LabelledVirtualStack(int width, int height, ColorModel cm,
			String path) {
		super(width, height, cm, path);
		labels = new String[getSize()];
	}

	public void addSlice(String sliceLabel, String fileName)
	{
		super.addSlice(fileName);
		if(getSize() > labels.length)
			labels = Arrays.copyOf(labels, getSize());
		
		labels[getSize() - 1] = sliceLabel;
	}

	@Override
	public String getSliceLabel(int n) {
		if(n > labels.length || labels[n-1] == null)
			return super.getSliceLabel(n);
		
		return (n == 1 ?
				labels[n-1] + TWO_HUNDRED_SPACES : // Just... don't ask.
				labels[n-1]
			);
	}
}
