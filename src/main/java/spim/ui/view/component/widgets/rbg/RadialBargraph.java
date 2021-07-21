package spim.ui.view.component.widgets.rbg;


import javafx.scene.control.Skin;
import spim.ui.view.component.widgets.rbg.skin.RadialBargraphSkin;

/**
 * Created by moon on 1/29/16.
 */
public class RadialBargraph	extends
														eu.hansolo.enzo.gauge.RadialBargraph
{
	// ******************** Style related *************************************
	@Override
	protected Skin createDefaultSkin()
	{
		return new RadialBargraphSkin(this);
	}

	@Override
	public String getUserAgentStylesheet()
	{
		return getClass().getResource("css/radial-bargraph.css")
											.toExternalForm();
	}
}
