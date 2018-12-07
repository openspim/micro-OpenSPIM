package spim.ui.view.component.xyzr3d.controls;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.DoublePropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.css.PseudoClass;
import javafx.css.StyleableProperty;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

/**
 * Created by moon on 12/2/15.
 */
public class CircleIndicator extends Control
{

	/**
	 * Value for progress indicating that the progress is indeterminate.
	 *
	 * @see #setProgress
	 */
	public static final double INDETERMINATE_PROGRESS = -1;

	/***************************************************************************
	 * * Constructors * *
	 **************************************************************************/
	/**
	 * Initialize the style class to 'progress-indicator'.
	 *
	 * This is the selector class from which CSS can be used to style this
	 * control.
	 */
	private static final String DEFAULT_STYLE_CLASS = "progress-indicator";
	/**
	 * Pseudoclass indicating this is a determinate (i.e., progress can be
	 * determined) progress indicator.
	 */
	private static final PseudoClass PSEUDO_CLASS_DETERMINATE = PseudoClass.getPseudoClass("determinate");
	/***************************************************************************
	 * * Properties * *
	 **************************************************************************/
	/**
	 * Pseudoclass indicating this is an indeterminate (i.e., progress cannot be
	 * determined) progress indicator.
	 */
	private static final PseudoClass PSEUDO_CLASS_INDETERMINATE = PseudoClass.getPseudoClass("indeterminate");
	/**
	 * A flag indicating whether it is possible to determine the progress of the
	 * ProgressIndicator. Typically indeterminate progress bars are rendered with
	 * some form of animation indicating potentially "infinite" progress.
	 */
	private ReadOnlyBooleanWrapper indeterminate;
	/**
	 * The actual progress of the ProgressIndicator. A negative value for progress
	 * indicates that the progress is indeterminate. A positive value between 0
	 * and 1 indicates the percentage of progress where 0 is 0% and 1 is 100%. Any
	 * value greater than 1 is interpreted as 100%.
	 */
	private DoubleProperty progress;

	/**
	 * Creates a new indeterminate ProgressIndicator.
	 */
	public CircleIndicator()
	{
		this(INDETERMINATE_PROGRESS);
	}

	/**
	 * Creates a new ProgressIndicator with the given progress value.
	 */
	public CircleIndicator(double progress)
	{
		// focusTraversable is styleable through css. Calling setFocusTraversable
		// makes it look to css like the user set the value and css will not
		// override. Initializing focusTraversable by calling applyStyle with null
		// StyleOrigin ensures that css will be able to override the value.
		((StyleableProperty<Boolean>) focusTraversableProperty()).applyStyle(	null,
																																					Boolean.FALSE);
		setProgress(progress);
		getStyleClass().setAll(DEFAULT_STYLE_CLASS);
		setAccessibleRole(AccessibleRole.PROGRESS_INDICATOR);

		// need to initialize pseudo-class state
		final int c = Double.compare(INDETERMINATE_PROGRESS, progress);
		pseudoClassStateChanged(PSEUDO_CLASS_INDETERMINATE, c == 0);
		pseudoClassStateChanged(PSEUDO_CLASS_DETERMINATE, c != 0);
	}

	public final boolean isIndeterminate()
	{
		return indeterminate == null ? true : indeterminate.get();
	}

	private void setIndeterminate(boolean value)
	{
		indeterminatePropertyImpl().set(value);
	}

	public final ReadOnlyBooleanProperty indeterminateProperty()
	{
		return indeterminatePropertyImpl().getReadOnlyProperty();
	}

	private ReadOnlyBooleanWrapper indeterminatePropertyImpl()
	{
		if (indeterminate == null)
		{
			indeterminate = new ReadOnlyBooleanWrapper(true)
			{
				@Override
				protected void invalidated()
				{
					final boolean active = get();
					pseudoClassStateChanged(PSEUDO_CLASS_INDETERMINATE, active);
					pseudoClassStateChanged(PSEUDO_CLASS_DETERMINATE, !active);
				}

				@Override
				public Object getBean()
				{
					return CircleIndicator.this;
				}

				@Override
				public String getName()
				{
					return "indeterminate";
				}
			};
		}
		return indeterminate;
	}

	/***************************************************************************
	 * * Methods * *
	 **************************************************************************/

	public final double getProgress()
	{
		return progress == null ? INDETERMINATE_PROGRESS : progress.get();
	}

	/***************************************************************************
	 * * Stylesheet Handling * *
	 **************************************************************************/

	public final void setProgress(double value)
	{
		progressProperty().set(value);
	}

	public final DoubleProperty progressProperty()
	{
		if (progress == null)
		{
			progress = new DoublePropertyBase(-1.0)
			{
				@Override
				protected void invalidated()
				{
					setIndeterminate(getProgress() < 0.0);
				}

				@Override
				public Object getBean()
				{
					return CircleIndicator.this;
				}

				@Override
				public String getName()
				{
					return "progress";
				}
			};
		}
		return progress;
	}

	/** {@inheritDoc} */
	@Override
	protected Skin<?> createDefaultSkin()
	{
		return new CircleIndicatorSkin(this);
	}

	/**
	 * Most Controls return true for focusTraversable, so Control overrides this
	 * method to return true, but ProgressIndicator returns false for
	 * focusTraversable's initial value; hence the override of the override. This
	 * method is called from CSS code to get the correct initial value.
	 * 
	 * @treatAsPrivate implementation detail
	 */
	@Deprecated
	@Override
	protected/*do not make final*/Boolean impl_cssGetFocusTraversableInitialValue()
	{
		return Boolean.FALSE;
	}

	/***************************************************************************
	 * * Accessibility handling * *
	 **************************************************************************/

	@Override
	public Object queryAccessibleAttribute(	AccessibleAttribute attribute,
																					Object... parameters)
	{
		switch (attribute)
		{
		case VALUE:
			return getProgress();
		case MAX_VALUE:
			return 360.0;
		case MIN_VALUE:
			return 0.0;
		case INDETERMINATE:
			return isIndeterminate();
		default:
			return super.queryAccessibleAttribute(attribute, parameters);
		}
	}

}
