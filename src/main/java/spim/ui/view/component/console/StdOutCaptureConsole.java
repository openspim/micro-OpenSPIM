package spim.ui.view.component.console;

import halcyon.view.ConsolePane;
import halcyon.view.console.TextAppender;
import javafx.scene.layout.BorderPane;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

/**
 * Standard Output and Error capture console
 */
public class StdOutCaptureConsole extends BorderPane
{
	private final ConsolePane consolePane;

	/**
	 * Instantiates a new Standard output/error capture console.
	 */
	public StdOutCaptureConsole()
	{
		consolePane = new ConsolePane();
		setCenter( consolePane );

		try
		{
			System.setOut( new StreamAppender( "INFO", consolePane, System.out ) );
			System.setErr( new StreamAppender( "WARN", consolePane, System.err ) );
		}
		catch ( UnsupportedEncodingException e )
		{
			e.printStackTrace();
		}
	}

	/**
	 * The Stream appender for output stream.
	 */
	public class StreamAppender extends PrintStream
	{
		final private StringBuilder buffer;
		final private String prefix;
		final private TextAppender textAppender;

		StreamAppender( String prefix, TextAppender consumer, PrintStream old ) throws UnsupportedEncodingException
		{
			super( old, true, "UTF-8" );

			this.prefix = prefix;
			this.buffer = new StringBuilder( 128 );
			buffer.append( "[" ).append( prefix ).append( "] " );
			this.textAppender = consumer;
		}

		@Override
		public void write( byte buf[], int off, int len )
		{
			try
			{
				String string = new String( buf, off, len );
				buffer.append( string );

				if ( string.endsWith( System.lineSeparator() ) )
				{
					String outString = buffer.toString();
					textAppender.appendText( outString );

					buffer.delete( 0, buffer.length() );
					buffer.append( "[" ).append( prefix ).append( "] " );
				}

				out.write( buf, off, len );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
	}
}
