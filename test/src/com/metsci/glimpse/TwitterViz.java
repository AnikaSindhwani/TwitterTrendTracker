package com.metsci.glimpse;

import io.indico.Indico;
import io.indico.api.results.IndicoResult;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.Map.Entry;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.DefaultDrawingSupplier;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.AbstractXYZDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.ui.Align;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleInsets;
import org.jfree.ui.RefineryUtilities;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class TwitterViz
{
    private static String topic;
    private static boolean Recalculate = false;
    private static HashMap<String, Point2D.Double[]> geoMap;
    private static HashMap<String, Double> sentimentMap;
    private static TreeMap<Date, String> weightage = new TreeMap<Date, String>( );
    private static HashMap<String, Integer> followers = new HashMap<String, Integer>();
    private static double min;
    private static double max;
    private static double sigma;
    public TwitterViz( String topic )
    {
        TwitterViz.topic = topic;
    }

    @SuppressWarnings( { "unchecked", "rawtypes" } )
    public static void main( String[] args )
    {
        //find all utterances of topic in tweet and hashtag        
        new TwitterViz( "Megan" );

        //read in file of tweets
        try ( BufferedReader br = new BufferedReader( new FileReader( "C:/Users/sindhwani/New folder/twitter/geo-1387780807.json" ) ) )
        {
            //read in twitter data, extract tweets, geolocation, and dates
            String line;
            geoMap = new HashMap<String, Point2D.Double[]>( );
            LinkedHashMap<Date, ArrayList<String>> dateMap = new LinkedHashMap<Date, ArrayList<String>>( );
            while ( ( line = br.readLine( ) ) != null )
            {
                line = br.readLine( );
                JSONTokener tokener = new JSONTokener( line );
                JSONObject main = new JSONObject( tokener );

                //if the tweet relates to the topic (by containing the word), it gets added to the list
                //XXX future application: do based on related words as well ("thesaurus" which has similar words)
                //XXX make sure to discount/exclude words like 'the', 'and', 'is', ...
                //XXX account for words within words that aren't correlated (i.e. card --> cardinal, cardio, Ricardo, cardíaco (words in different languages))
                //XXX for words in different languages, do we base correlation check on matching hashtags, or translation somehow?
                //XXX can discount names/accounts by not including twitter handles (so taking out "@___" when checking)

                if ( !main.isNull( "text" ) && main.getString( "text" ).toLowerCase( ).contains( topic.toLowerCase( ) ) )
                {
                    int index = main.getString( "text" ).indexOf( topic );
                    while ( index >= 0 )
                    {
                        //if char before and/or char after are alphabetical characters, don't add them
                        //or if the char is '@', then don't count it (to exclude names and such) --> unless this is already taken care of by the previous statement?
                        //if(Character.isLetter(main.charAt(index-1)))
                        //    break;
                        //boolean = true/false
                        index = main.getString( "text" ).indexOf( topic, index + 1 );
                    }
                    //System.out.println( main.getString( "text" ) );

                    if ( main.has( "place" ) && !main.isNull( "place" ) )
                    {
                        if ( main.getJSONObject( "place" ).has( "bounding_box" ) && main.getJSONObject( "place" ).optJSONObject( "bounding_box" ) != null )

                        if ( main.getJSONObject( "place" ).getJSONObject( "bounding_box" ).has( "coordinates" ) )
                        {
                            JSONArray box = main.getJSONObject( "place" ).getJSONObject( "bounding_box" ).getJSONArray( "coordinates" ).getJSONArray( 0 );
                            Point2D.Double[] boundingBox = new Point2D.Double[4];
                            for ( int i = 0; i < box.length( ); i++ )
                            {
                                JSONArray coordinates = box.getJSONArray( i );
                                boundingBox[i] = new Point2D.Double( coordinates.getDouble( 0 ), coordinates.getDouble( 1 ) );
                            }
                            if ( geoMap.containsKey( main.getString( "text" ) ) )
                                geoMap.put( main.getString( "text" ) + "\u0000", boundingBox );
                            else
                                geoMap.put( main.getString( "text" ), boundingBox );
                        }
                    }
                    else if ( main.has( "geo" ) && !main.isNull( "geo" ) )
                    {
                        if ( main.has( "coordinates" ) )
                        {
                            JSONArray coordinates = main.getJSONObject( "geo" ).getJSONArray( "coordinates" );
                            Point2D.Double[] boundingBox = new Point2D.Double[1];
                            boundingBox[0] = new Point2D.Double( coordinates.getDouble( 1 ), coordinates.getDouble( 0 ) );
                            if ( geoMap.containsKey( main.getString( "text" ) ) )
                                geoMap.put( main.getString( "text" ) + "\u0000", boundingBox );
                            else
                                geoMap.put( main.getString( "text" ), boundingBox );
                        }
                    }
                    String date = "";
                    if ( main.has( "created_at" ) && !main.isNull( "created_at" ) )
                    {
                        DateFormat df = new SimpleDateFormat( "EEE MMM dd kk:mm:ss yyyy" );
                        date = main.getString( "created_at" );
                        date = date.substring( 0, date.lastIndexOf( '+' ) ) + date.substring( date.length( ) - 4 );
                        if ( dateMap.containsKey( df.parse( date ) ) )
                        {
                            ArrayList<String> tweets = dateMap.get( df.parse( date ) );
                            tweets.add( main.getString( "text" ) );
                            dateMap.put( df.parse( date ), tweets );
                        }
                        else
                            dateMap.put( df.parse( date ), new ArrayList<String>( Arrays.asList( main.getString( "text" ) ) ) );
                    }
                    
                    int numFollowers = 0;
                    if ( main.has( "user" ) && !main.isNull( "user" ) ) 
                        if ( main.getJSONObject( "user" ).has( "followers_count" ) && !main.getJSONObject( "user" ).isNull( "followers_count" ) )
                        {
                            numFollowers = main.getJSONObject( "user" ).getInt( "followers_count" );
                            followers.put( main.getString("text"), numFollowers );
                        }

                    //find tweet with most weightage per minute
                    //date/time --> tweet --> numFollowers //replace if numFollowers is greater
                    DateFormat df = new SimpleDateFormat( "EEE MMM dd kk:mm:ss yyyy" ); 
                    if ( weightage.containsKey( df.parse( date ) ) )                        
                    {
                        if ( numFollowers > followers.get( weightage.get( df.parse( date ) )) )
                        {
                            weightage.put( df.parse(date), main.getString("text") );
                        }
                    }
                    else 
                    {
                        weightage.put(df.parse( date ), main.getString("text"));
                    }
                            

                    /*if(main.has("in_reply_to") && !main.isNull( "in_reply_to" ))
                    {
                    
                    }*/
                }

            }
            
            if(geoMap.isEmpty())
            {
                System.out.println("This string was not found.");
                return; 
            }
            
            
            //calculate or read in sentiment analysis data (from Indico)
            sentimentMap = new HashMap<String, Double>( geoMap.size( ) );
            if ( Recalculate )
            {
                Indico indico = new Indico( "b14b01132cdfeac81333566b03650fd0" );
                for ( String s : geoMap.keySet( ) )
                {
                    try
                    {
                        IndicoResult single = indico.sentiment.predict( s );
                        Double result = single.getSentiment( );
                        System.out.println( "result = " + result );
                        sentimentMap.put( s, result );
                    }
                    catch ( Exception e )
                    {
                        System.out.println( "illegal argument found" );
                    }
                }

                FileOutputStream fos = new FileOutputStream( System.getProperty( "user.home" ) + File.separator + "/SentimentTable.hashmap" );
                ObjectOutputStream oos = new ObjectOutputStream( fos );
                oos.writeObject( sentimentMap );
                oos.close( );
                fos.close( );
            }
            else
            {
                try
                {
                    FileInputStream fis = new FileInputStream( System.getProperty( "user.home" ) + File.separator + "/SentimentTable807.hashmap" );
                    ObjectInputStream ois = new ObjectInputStream( fis );
                    sentimentMap = ( HashMap<String, Double> ) ois.readObject( );
                    ois.close( );
                    fis.close( );
                }
                catch ( ClassNotFoundException e )
                {
                    e.printStackTrace( );
                }
            }
            
            
            //get average sentiment per minute
            Iterator it = dateMap.entrySet( ).iterator( );
            Entry<Date, ArrayList<String>> next = ( Entry<Date, ArrayList<String>> ) it.next( );
            TreeMap<Date, List<Double>> sentimentByTime = new TreeMap<Date, List<Double>>();
            DateFormat df = new SimpleDateFormat( "MMM dd kk:mm:ss yyyy" ); 
            while ( it.hasNext( ) )
            {
                ArrayList<String> tweets = next.getValue( );
                double totalSentiment = 0.0;
                for ( String text : tweets )
                {
                    if ( sentimentMap.containsKey( text ) )
                    {
                        totalSentiment += sentimentMap.get( text );
                    }
                    else
                        totalSentiment += .5; //have to add it here b/c avg is calculated from tweets.size, numbers scaled from [0,1] to [-.5, .5] 
                }
                Date date = df.parse(df.format(next.getKey()));
                if(sentimentByTime.containsKey(date))
                {
                    sentimentByTime.put( date, Arrays.asList( totalSentiment + sentimentByTime.get(date).get(0), tweets.size() + sentimentByTime.get(date).get(1)) ); //**may not work
                }
                else
                {
                    sentimentByTime.put( date, Arrays.asList( totalSentiment, tweets.size()*1.0 ) ); //**may not work
                }
                //avgSentiment = avgSentiment / tweets.size( ) - .5;
                next = ( Entry<Date, ArrayList<String>> ) it.next( );
            }
            
            sigma = 10;//1/geoMap.size( );
            
            createGraph(topic, sentimentByTime);
        }
        catch ( Exception e )
        {
            e.printStackTrace( );
        }
    }
    private static HashMap<Integer, List<Double>> storedSentiment = new HashMap<Integer, List<Double>>();
    private static void createGraph(String topic, TreeMap<Date, List<Double>> sentiment) throws MalformedURLException, IOException
    {
        JFreeChart timeChart = ChartFactory.createScatterPlot( "TwitterViz: \""+topic+"\"", "Time", "Frequency", createTimeDataset(topic, sentiment), PlotOrientation.VERTICAL, true, true, false );
        
        XYPlot timePlot = (XYPlot) timeChart.getPlot(); 
        Renderer renderer = new Renderer(false, true);
        timePlot.setRenderer(renderer);
        renderer.setSeriesShape( 0, DefaultDrawingSupplier.createStandardSeriesShapes()[0] );
        renderer.setSeriesPaint( 0, Color.BLUE);
        renderer.setSeriesLinesVisible( 0, true );
        timePlot.setRenderer(renderer);
        ChartPanel timePanel = new ChartPanel(timeChart, false);
        ValueAxis domainAxis = new DateAxis("Time");
        timePlot.setDomainAxis(domainAxis);
        timePanel.getChart( ).removeLegend( );
        timePanel.setPreferredSize( new java.awt.Dimension(560, 367) );
        //timePanel.chartChanged( event );
        //System.out.println(timePanel);
        
        NumberAxis xAxis = new NumberAxis();
        xAxis.setAxisLineVisible( false );
        xAxis.setMinorTickMarksVisible( true );
        NumberAxis yAxis = new NumberAxis();
        yAxis.setAxisLineVisible( false );
        yAxis.setMinorTickMarksVisible( true );
        
        XYBlockRenderer blockRenderer = new XYBlockRenderer(); 
        myXYPlot heatPlot = new myXYPlot(new XYZArrayDataset( generateHeatData(1000, 500) ), xAxis, yAxis, blockRenderer);
        heatPlot.setBackgroundPaint(Color.lightGray);
        min = Math.round(min*1e2)/1e2;
        max = Math.round(max*1e2)/1e2;
        double range = Math.max(Math.abs(min), Math.abs(max));
        LookupPaintScale paintScale = new LookupPaintScale(-range, range, new Color(128, 128, 128, 0));
        
        for(double k = -range; k < 0; k+=.1)
        {
            k = Math.round(k*1e2)/1e2;
            paintScale.add(k, new Color(255, 0, 0, (int)(25.5*Math.abs(k))));
            //paintScale.add(k, new Color((int)(Math.max( 0, 255-(128/range)*(k+range))), Math.min( 255, (int)((128/range)*(k+range))), 0, 100)); //k[-1,1] R[0,256]
        }
        paintScale.add(0, new Color(0, 0, 0, 0));
        for(double k = 0; k < range; k+=.1)
        {
            k = Math.round(k*1e2)/1e2;
            paintScale.add(k, new Color(0, 255, 0, (int)(25.5*k)));
            //paintScale.add(k, new Color((int)(Math.max( 0, 255-(128/range)*(k+range))), Math.min( 255, (int)((128/range)*(k+range))), 0, 100)); //k[-1,1] R[0,256]
        }
        blockRenderer.setPaintScale( paintScale );
        
        JFreeChart heatChart = new JFreeChart(heatPlot);
        heatChart.removeLegend( );
        NumberAxis scaleAxis = new NumberAxis("Scale");
        scaleAxis.setAxisLinePaint(Color.white);
        scaleAxis.setTickMarkPaint(Color.white);
        scaleAxis.setTickLabelFont(new Font("Dialog", Font.PLAIN, 7));
        scaleAxis.setRange(-range, range);
        PaintScaleLegend legend = new PaintScaleLegend(paintScale, scaleAxis);
        legend.setAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
        legend.setAxisOffset(5.0);
        legend.setMargin(new RectangleInsets(5, 5, 5, 5));
        legend.setFrame(new BlockBorder(Color.red));
        legend.setPadding(new RectangleInsets(10, 10, 10, 10));
        legend.setStripWidth(10);
        legend.setPosition(RectangleEdge.RIGHT);
        heatChart.addSubtitle(legend);
        
        Image map = ImageIO.read(new URL("http://worldtraveladventuresbat.com/wp-content/themes/sw_go/assets/img/guidemap.gif"));
        heatPlot.setBackgroundImage( map );
        
        ChartPanel heatPanel = new ChartPanel(heatChart, false);
        heatPanel.setPreferredSize( new java.awt.Dimension(560, 367) );
        
        
        JFrame frame = new JFrame();
        frame.getContentPane( ).add( timePanel, BorderLayout.WEST );
        frame.getContentPane( ).add( heatPanel, BorderLayout.EAST);
        //frame.setContentPane( timePanel );
        frame.setSize( 1150, 400 );
        frame.setPreferredSize( new java.awt.Dimension(1150, 400) );
        frame.setVisible( true );
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        
        RefineryUtilities.centerFrameOnScreen( frame );
        
        timePanel.addMouseListener( new MouseListener( )
        {
            
            @Override
            public void mouseReleased( MouseEvent e )
            {
                // TODO Auto-generated method stub
                //System.out.println(timePanel.getChart( ).getXYPlot( ).getDomainAxis().getLowerMargin( ));
            }
            
            @Override
            public void mousePressed( MouseEvent e )
            {
                // TODO Auto-generated method stub
                //System.out.println(e.getX()+" "+timePanel.getMousePosition( )+" "+timePanel.translateScreenToJava2D( new java.awt.Point(e.getX(), e.getY() )));
                //e.getY();
            }
            
            @Override
            public void mouseExited( MouseEvent e )
            {
                // TODO Auto-generated method stub
            }
            
            @Override
            public void mouseEntered( MouseEvent e )
            {
                // TODO Auto-generated method stub
            }
            
            @Override
            public void mouseClicked( MouseEvent e )
            {
                // TODO Auto-generated method stub
                //double zoomValue = timePanel.getZoomInFactor( ); //is this necessary, or can we simply get the plot bounds.
            }
        } );
        
        
    }
    
    private static class Renderer extends XYLineAndShapeRenderer {
        private static final long serialVersionUID = 1L;
        public Renderer(boolean lines, boolean shapes) {
            super(lines, shapes);
        }

        @Override
        public Paint getItemPaint(int row, int col) {
            double avgSentiment = storedSentiment.get(col).get(0)/storedSentiment.get(col).get(1);
            if(avgSentiment > .5)
                return Color.green;
            else
                return Color.red;
        }
        @Override
        protected void drawFirstPassShape(Graphics2D g2, int pass, int series,
            int item, Shape shape) {
            g2.setStroke(getItemStroke(series, item));
            g2.setPaint(Color.black);
            g2.draw(shape);
        }
    }
    @SuppressWarnings( { "unchecked", "rawtypes" } )
    private static TimeSeriesCollection createTimeDataset(String topic, TreeMap<Date, List<Double>> sentiment)
    {
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries data = new TimeSeries("\""+topic+"\"");
        Iterator it = sentiment.entrySet( ).iterator( );
        Entry<Date, List<Double>> next = ( Entry<Date, List<Double>> ) it.next( );
        while ( it.hasNext( ) )
        {
            if(data.getDataItem(new Minute(next.getKey())) == null)
            {
                data.add( new Minute(next.getKey()), next.getValue().get(1) );
                storedSentiment.put( data.getIndex(new Minute(next.getKey( ))), Arrays.asList(next.getValue().get(0).doubleValue( ), next.getValue().get(1).doubleValue( )) );
            }
            else
            {
                data.addOrUpdate( new Minute(next.getKey()), next.getValue().get(1).intValue( ) + data.getValue( new Minute(next.getKey()) ).intValue( ) );
                storedSentiment.put( data.getIndex(new Minute(next.getKey( ))), Arrays.asList( next.getValue().get(0).doubleValue( )+storedSentiment.get( data.getIndex(new Minute(next.getKey( ))) ).get( 0 ), next.getValue().get(1).doubleValue( )+storedSentiment.get( data.getIndex(new Minute(next.getKey( ))) ).get( 1 )) );
                //System.out.println(data.getIndex(new Minute(next.getKey( ))) + " " + storedSentiment.get(data.getIndex(new Minute(next.getKey( )))));
            }
            next = ( Entry<Date, List<Double>> ) it.next( );
        }
        dataset.addSeries( data );
        return dataset;
    }
    private static class myXYPlot extends XYPlot {

        private static final long serialVersionUID = 1L;
        private double X_Min = -50;    //chart overall (0% zoom) x min val 
        private double X_Max = 1050;   
        private double Y_Min = -50;    
        private double Y_Max = 550;   
        private myXYPlot(XYDataset dataset, ValueAxis domainAxis,
         ValueAxis rangeAxis, XYItemRenderer renderer) {
            super(dataset, domainAxis, rangeAxis, renderer);
        }

       /**
        * Draws the background image (if there is one) aligned within the specified
        * area and zoomed to the current zoom level.
        * 
        * @param g2
        *            the graphics device.
        * @param area
        *            the area.
        */
       public void drawBackgroundImage(Graphics2D g2, Rectangle2D area) {
          Image backgroundImage = super.getBackgroundImage();
          float backgroundAlpha = super.getBackgroundAlpha();
          int backgroundImageAlignment = super.getBackgroundImageAlignment();
    
          if (backgroundImage != null) {
             Composite originalComposite = g2.getComposite();
             g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC,
                   backgroundAlpha));
             Rectangle2D dest = new Rectangle2D.Double(0.0, 0.0, backgroundImage
                   .getWidth(null), backgroundImage.getHeight(null));
             Align.align(dest, area, backgroundImageAlignment);
    
             double cw = Math.abs(X_Min) + Math.abs(X_Max);      //chart abs. width
             double ch = Math.abs(Y_Min) + Math.abs(Y_Max);      //chart abs. heigth
             float bw = backgroundImage.getWidth(null);               //pic width
             float bh = backgroundImage.getHeight(null);            //pic heigth
             
             //scale zoom rectangle to size of pic (all 4 edges)
             float x_min_rat = Math.abs(
                      (float)((X_Min - getDomainAxis().getLowerBound()) / (cw/bw))
                   );
             float x_max_rat = Math.abs(
                      (float)((X_Max - getDomainAxis().getUpperBound()) / (cw/bw))
                   );
             
             float y_min_rat = Math.abs(
                      (float)((Y_Min - getRangeAxis().getLowerBound()) / (ch/bh))
                   );
             float y_max_rat = Math.abs(
                      (float)((Y_Max - getRangeAxis().getUpperBound()) / (ch/bh))
                   );
             //and draw from src rect of pic to dest rect in g2
             ((Graphics) g2).drawImage(backgroundImage,
                   (int) dest.getX(),                     //dest x1
                   (int) dest.getY(),                     //dest y1
                   (int)(dest.getX() + dest.getWidth() + 1),      //dest x2
                   (int)(dest.getY() + dest.getHeight() + 1),      //dest y2
                   (int) Math.round(x_min_rat),               //src  x1 
                   (int) Math.round(y_max_rat),               //src  y1
                   (int) Math.round( bw-(x_max_rat) ),         //src  x2
                   (int) Math.round( bh-(y_min_rat) ),         //src  y2
                   null);
          
             g2.setComposite(originalComposite);
          }
       }

    }
    public static double[][] generateHeatData( int sizeX, int sizeY )
    {
        // generate some data to display
        double[][] sumArr = new double[0][];
       // double[][] sumArrPrim = new double[0][];
        //Gaussian Calculation
        sumArr = new double[sizeX][sizeY];
        for ( double[] row : sumArr )
            Arrays.fill( row, 0.0 );
        max = Double.NEGATIVE_INFINITY;
        min = Double.POSITIVE_INFINITY;
        for ( String text : geoMap.keySet( ) )
        {
            Point2D.Double[] pointArr = geoMap.get( text );
            Double avgx = 0.0;
            Double avgy = 0.0;
            for ( Point2D.Double aDouble : pointArr )
            {
                avgx += aDouble.getX( );
                avgy += aDouble.getY( );
            }
            avgx = avgx / pointArr.length;
            avgy = avgy / pointArr.length;

            for ( int x = 0; x < sumArr.length; x++ )
            {
                for ( int y = 0; y < sumArr[0].length; y++ )
                {
                    Double sentiment = 0.0;
                    if ( sentimentMap.containsKey( text ) ) sentiment = sentimentMap.get( text ) - .5;
                    //sumArr[x][y] += sentiment * Math.exp(-(Math.pow((x) * ResolutionDiff - avgx + 180,2)/ 2 + Math.pow((y * ResolutionDiff - avgy + 90),2) / 2));
                    double gx = -180 + 360 * ( ( double ) x / ( double ) ( sumArr.length - 1 ) );
                    double gy = -90 + 180 * ( ( double ) y / ( double ) ( sumArr[0].length - 1 ) );
                    double xd = gx - avgx;
                    double yd = gy - avgy;
                    //double xd = x-avgx;
                    //double yd = y-avgy;

                    sumArr[x][y] += sentiment * Math.exp( - ( xd * xd  + yd * yd ) / (2 * sigma * sigma) );
                    if(sumArr[x][y] < 0.001 && sumArr[x][y] > -0.001)
                    {
                        //System.out.println("check");
                        sumArr[x][y] = 0d;
                    }
                    max = Math.max( max, sumArr[x][y] );
                    min = Math.min( min, sumArr[x][y] );
                }
            }
        }

       /* 

        sumArrPrim = new double[sumArr.length][sumArr[0].length];
        for ( int i = 0; i < sumArr.length; i++ )
        {
            for ( int j = 0; j < sumArr[0].length; j++ )
            {
                //sumArr[i][j] = Math.pow(10, 13) * sumArr[i][j];
                //System.out.print( sumArr[i][j] + " " );
                if ( Double.isNaN( sumArr[i][j] ) )
                {
                    sumArr[i][j] = 0d;
                }
                
                sumArrPrim[i][j] = Math.log10( sumArr[i][j].doubleValue( ) )/100;
                if(sumArrPrim[i][j] > -1000)
                {
                    max = Math.max( max, sumArrPrim[i][j] );
                    min = Math.min( min, sumArrPrim[i][j] );
                }
                if(sumArrPrim[i][j] >= 0.01 || sumArrPrim[i][j] <= -0.01)
                    System.out.println(sumArr[i][j]);
            }
            //System.out.print( "\n" );
        }
        System.out.println( min + " " + max );*/
        return sumArr;
    }
    private static class XYZArrayDataset extends AbstractXYZDataset{
        private static final long serialVersionUID = 1L;
        double[][] data;
        int rowCount = 0;
        int columnCount = 0;
        
        XYZArrayDataset(double[][] data){
           this.data = data;
           rowCount = data.length;
           columnCount = data[0].length;
        }
        public int getSeriesCount(){
           return 1;
        }
        @SuppressWarnings( "rawtypes" )
        public Comparable getSeriesKey(int series){
           return "serie";
        }
        public int getItemCount(int series){
           return rowCount*columnCount;
        }
        public double getXValue(int series,int item){
           return (int)(item/columnCount);
        }
        public double getYValue(int series,int item){
           return item % columnCount;
        }
        public double getZValue(int series,int item){
           return data[(int)(item/columnCount)][item % columnCount];
        }
        public Number getX(int series,int item){
           return new Double((int)(item/columnCount));
        }
        public Number getY(int series,int item){
           return new Double(item % columnCount);
        }
        public Number getZ(int series,int item){
           return new Double(data[(int)(item/columnCount)][item % columnCount]);
        }
    }

}