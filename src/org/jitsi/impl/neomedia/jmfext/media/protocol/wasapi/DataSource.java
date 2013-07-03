/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

import java.io.*;
import java.util.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.util.*;

/**
 * Implements <tt>CaptureDevice</tt> and <tt>DataSource</tt> using Windows Audio
 * Session API (WASAPI) and related Core Audio APIs such as Multimedia Device
 * (MMDevice) API.
 *
 * @author Lyubomir Marinov
 */
public class DataSource
    extends AbstractPushBufferCaptureDevice
{
    /**
     * The <tt>Logger</tt> used by the <tt>DataSource</tt> class and its
     * instances to log debugging information.
     */
    private static final Logger logger = Logger.getLogger(DataSource.class);

    /**
     * The indicator which determines whether the voice capture DMO is to be
     * used to perform echo cancellation and/or noise reduction.
     */
    final boolean aec;

    /**
     * The <tt>WASAPISystem</tt> which has contributed this
     * <tt>CaptureDevice</tt>/<tt>DataSource</tt>.
     */
    final WASAPISystem audioSystem;

    /**
     * Initializes a new <tt>DataSource</tt> instance.
     */
    public DataSource()
    {
        this(null);
    }

    /**
     * Initializes a new <tt>DataSource</tt> instance with a specific
     * <tt>MediaLocator</tt>.
     *
     * @param locator the <tt>MediaLocator</tt> to initialize the new instance
     * with
     */
    public DataSource(MediaLocator locator)
    {
        super(locator);

        audioSystem
            = (WASAPISystem)
                AudioSystem.getAudioSystem(AudioSystem.LOCATOR_PROTOCOL_WASAPI);
        aec = audioSystem.isDenoise() || audioSystem.isEchoCancel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected WASAPIStream createStream(
            int streamIndex,
            FormatControl formatControl)
    {
        return new WASAPIStream(this, formatControl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doConnect()
        throws IOException
    {
        super.doConnect();

        MediaLocator locator = getLocator();

        synchronized (getStreamSyncRoot())
        {
            for (Object stream : getStreams())
                ((WASAPIStream) stream).setLocator(locator);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doDisconnect()
    {
        try
        {
            synchronized (getStreamSyncRoot())
            {
                for (Object stream : getStreams())
                {
                    try
                    {
                        ((WASAPIStream) stream).setLocator(null);
                    }
                    catch (IOException ioe)
                    {
                        logger.error(
                                "Failed to disconnect "
                                    + stream.getClass().getName(),
                                ioe);
                    }
                }
            }
        }
        finally
        {
            super.doDisconnect();
        }
    }

    Format[] getIAudioClientSupportedFormats()
    {
        return
            getIAudioClientSupportedFormats(
                    /* streamIndex */ 0,
                    audioSystem.getAECSupportedFormats());
    }

    private Format[] getIAudioClientSupportedFormats(
            int streamIndex,
            List<AudioFormat> aecSupportedFormats)
    {
        Format[] superSupportedFormats = super.getSupportedFormats(streamIndex);

        /*
         * If the capture endpoint device is report to support no Format, then
         * acoustic echo cancellation (AEC) will surely not work.
         */
        if ((superSupportedFormats == null)
                || (superSupportedFormats.length == 0))
            return superSupportedFormats;

        Format[] array;

        if (aecSupportedFormats.isEmpty())
            array = superSupportedFormats;
        else
        {
            /*
             * Filter out the Formats added to the list of supported because of
             * the voice capture DMO alone.
             */
            List<Format> list
                = new ArrayList<Format>(superSupportedFormats.length);

            for (Format superSupportedFormat : superSupportedFormats)
            {
                /*
                 * Reference equality to an aecSupportedFormat signals that the
                 * superSupportedFormat is not supported by the capture endpoint
                 * device and is supported by the voice capture DMO.
                 */
                boolean equals = false;

                for (Format aecSupportedFormat : aecSupportedFormats)
                {
                    if (superSupportedFormat == aecSupportedFormat)
                    {
                        equals = true;
                        break;
                    }
                }
                if (!equals)
                    list.add(superSupportedFormat);
            }
            array = list.toArray(new Format[list.size()]);
        }
        return array;
    }

    /**
     * {@inheritDoc}
     *
     * The <tt>Format</tt>s supported by this
     * <tt>CaptureDevice</tt>/<tt>DataSource</tt> are either the ones supported
     * by the capture endpoint device or the ones supported by the voice capture
     * DMO that implements the acoustic echo cancellation (AEC) feature
     * depending on whether the feature in question is disabled or enabled.
     */
    @Override
    protected Format[] getSupportedFormats(int streamIndex)
    {
        List<AudioFormat> aecSupportedFormats
            = audioSystem.getAECSupportedFormats();

        if (aec)
        {
            return
                aecSupportedFormats.toArray(
                        new Format[aecSupportedFormats.size()]);
        }
        else
        {
            return
                getIAudioClientSupportedFormats(
                        streamIndex,
                        aecSupportedFormats);
        }
    }
}
