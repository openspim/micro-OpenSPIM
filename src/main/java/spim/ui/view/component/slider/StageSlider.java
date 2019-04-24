package spim.ui.view.component.slider;

import javafx.beans.property.Property;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.util.converter.NumberStringConverter;
import spim.ui.view.component.slider.customslider.Slider;
import spim.ui.view.component.xyzr3d.controls.CircleIndicator;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: September 2018
 */
public class StageSlider extends HBox
{
	private final Label mLabel;
	private final Slider mSlider;
	private final TextField mTextField;

	//public StageSlider( String label, Property< Number > variable, boolean readOnly, boolean isR, double min, double max, double current, double tick )
	public StageSlider( String label, Property< Number > variable, boolean readOnly, boolean isR, double min, double max, double tick )
	{
		super();

		setAlignment( Pos.CENTER );
		setSpacing( 10 );

		mLabel = new Label( label );
		mLabel.setAlignment( Pos.CENTER );

		mSlider = new Slider();
		mSlider.setPrefWidth( 250 );
		mSlider.setBlockIncrement( tick );
		mSlider.setMajorTickUnit( tick );
		mSlider.setMinorTickCount( 5 );

		//		mSlider.setSnapToTicks( true );
		mSlider.setShowTickLabels( true );
		mSlider.setShowTickMarks( true );
		mSlider.setMin( min );
		mSlider.setMax( max );
//		mSlider.setValue( current );

		mTextField = new TextField();
		mTextField.setAlignment( Pos.CENTER );
		mTextField.setPrefWidth( 80 );
		mTextField.textProperty().bindBidirectional( mSlider.valueProperty(), new NumberStringConverter() );

		if ( readOnly )
		{
			mSlider.valueProperty().bind( variable );
			mSlider.setDisable( true );
			mSlider.setId( "read-only" );
			mTextField.setDisable( true );

			setPadding( new Insets( 5, 20, 10, 0 ) );
			mSlider.setPadding( new Insets( 0, 5, 0, 0 ) );
		}
		else
		{
			mTextField.setOnAction( event -> {
				variable.setValue( mSlider.valueProperty().doubleValue() );
			} );

			mSlider.valueProperty().addListener( ( obs, oldval, newVal ) ->
					mSlider.setValue( newVal.intValue() ) );

			mSlider.setOnMouseReleased( event -> variable.setValue( mSlider.getValue() ) );

//			variable.bind( mSlider.valueProperty() );
			mSlider.setShowTickLabels( false );

			setPadding( new Insets( 10, 20, 5, 0 ) );
			mSlider.setPadding( new Insets( 0, 5, 0, 0 ) );
		}

		if ( readOnly && isR )
		{
			final CircleIndicator pi = new CircleIndicator( 0 );
			pi.setPrefWidth( 80 );
			mSlider.valueProperty().addListener(
					( ObservableValue< ? extends Number > ov, Number old_val, Number new_val ) ->
							pi.setProgress( new_val.doubleValue() ) );

			getChildren().addAll( mLabel, mSlider, pi );
		}
		else
		{
			getChildren().addAll( mLabel, mSlider, mTextField );
		}
	}

	public Slider getSlider()
	{
		return mSlider;
	}

	public TextField getTextField()
	{
		return mTextField;
	}
}
