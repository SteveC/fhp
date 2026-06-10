import java.util.*;
import java.lang.*;
import java.io.*;

public class fhp
{

  private static final byte RIGHT        = 1;
  private static final byte TOP_RIGHT    = 2;
  private static final byte TOP_LEFT     = 4;
  private static final byte LEFT         = 8;
  private static final byte BOTTOM_LEFT  = 16;
  private static final byte BOTTOM_RIGHT = 32;


  private int nWidth = 2048;
  private int nHeight = 1024;

  private byte lookupTable[][] = new byte[2][64];

  private int bouncePoints[][] = new int[1000][2];
  private int nBouncePointsCount = 0;
  private int nBouncePointsArraySize = 1000;

  private byte gasTable[][];
  private byte moveTable[][];

  public static void main(String[] args)
  {

    // test if run off the command line

    new fhp().test();

  } // main


  private fhp()
  {
    init();
  } // fhp


  private fhp(int width, int height)
  {
    nWidth = width;
    nHeight = height;

    init();

  } // fhp


  private void init()
  {
    generateLookupTable();
    createTables();
    initBouncePoints();
  } // init

  private void initBouncePoints()
  {
    // add bounce points at the top and bottom of the box

    for(int x = 0; x < nWidth - 1; x++)
    {
      addBouncePoint(x, 0);
      addBouncePoint(x, nHeight - 1);
    }

  } // initBouncePoints

  public void addBouncePoint(int x, int y)
  {
    // add a point to bounce gas off at x,y

    if( nBouncePointsArraySize - 1 == nBouncePointsCount )
    {
      // need to expand the array!

      nBouncePointsArraySize += 1000;

      int temp[][] = new int[nBouncePointsArraySize][2];

      System.arraycopy(bouncePoints, 0, temp, 0, nBouncePointsArraySize - 1000);

      bouncePoints = temp;

    }

    bouncePoints[nBouncePointsCount][0] = x;
    bouncePoints[nBouncePointsCount][1] = y;

    gasTable[x][y] = 0; // make sure there are no particles under the bounce point

    nBouncePointsCount++;

  } // addBouncePoint



  public byte[][] getGasArray()
  {

    return gasTable;

  } // getGasArray



  private void test()
  {
    // do some stuff to see if it works

    System.out.println("");
    System.out.println("Lookup table looks like this:");

    for(int i = 0; i<64; i++)
    {

      System.out.print(lookupTable[0][i] + "," + lookupTable[1][i] + " ");
    }

    System.out.println("");

    long lStartTime = System.currentTimeMillis();

    int testIterations = 100;

    System.out.println("Testing " + testIterations + " iterations of the model on a " + nWidth + " by " + nHeight + " size grid...");

    addCircle(); // add something to bounce off

    gasTable[10][10] = LEFT; // so there is at least one particl there to make the save work

    for(int i = 0; i < testIterations; i++)
    {
      String t = "" + i;
      
      while( t.length() < 3)
      {
        t = "0" + t;
      }
      
      saveGasDensityFile("test-gas-" + t + ".ppm");
     
      saveVelocityFile("test-vel-" + t );
      
      for(int j = 0; j < 1000; j ++)
        
      {
        forceMomentum(.3);
        iterate();
      }
    }


  } // test



  public void forceMomentum(double chance)
  {
    // force some momentum on an imaginary plate on the left
    // 

    Random r = new Random();

    for( int y = 1; y < nHeight; y++)
    {
      
      if( (gasTable[0][y] & RIGHT ) != 0 && (gasTable[0][y] & LEFT) ==0)
      {
        if(r.nextDouble() < chance)
        {
          gasTable[0][y] = (byte)( (gasTable[0][y] & TOP_RIGHT) + 
              (gasTable[0][y] & TOP_LEFT) + 
              (gasTable[0][y] & BOTTOM_LEFT) + 
              (gasTable[0][y] & BOTTOM_RIGHT) + LEFT);


        }

      }
      else
      {

        switch(gasTable[0][y])
        {
          case(BOTTOM_RIGHT + RIGHT + TOP_RIGHT + TOP_LEFT + LEFT):
            if( r.nextDouble() < chance )
            {
              gasTable[0][y] = RIGHT + TOP_RIGHT + TOP_LEFT + LEFT + BOTTOM_LEFT;

            }
            break;

          case(RIGHT + TOP_RIGHT + LEFT + BOTTOM_LEFT + BOTTOM_RIGHT):
            if( r.nextDouble() < chance)
            {
              gasTable[0][y] = RIGHT + TOP_LEFT + LEFT + BOTTOM_LEFT + BOTTOM_RIGHT;
            }
            break;

          case(RIGHT + LEFT + TOP_RIGHT + TOP_LEFT):
            if( r.nextDouble() < chance)
            {
              if( r.nextBoolean() )
              {
                gasTable[0][y] = RIGHT + LEFT + TOP_RIGHT + BOTTOM_LEFT;

              }
              else
              {
                gasTable[0][y] = RIGHT + LEFT + TOP_LEFT + BOTTOM_RIGHT;

              }

            }

        }
      }
    } 

  } // forceMomentum



  public void iterate()
  {
    byte tempTable[][] = new byte[nWidth][nHeight];

    Random r = new Random();

    int nR = r.nextInt();

    int counter = 32;

    for(int x = 0; x < nWidth; x++)
    {
      for(int y = 0; y < nHeight; y++)
      {

        counter--;

        if(counter == 0)
        {

          nR = r.nextInt();
          counter = 31;
        }

        nR = nR >> 1;


        /*
           System.out.println("nR :" + nR);        
           System.out.println("x :" + x);  
           System.out.println("y :" + y);
           System.out.println("gasTable[x][y]:" + gasTable[x][y]);
           System.out.println("lookupTable[nR & 1][gasTable[x][y]] :" + lookupTable[nR & 1][gasTable[x][y]]);
         */
        //if(gasTable[x][y] != 0)  
        //{
        //  System.out.println(x+ "," + y + ": " + gasTable[x][y] + " -> " + lookupTable[nR & 1][gasTable[x][y]] );

        //}
        tempTable[x][y] = lookupTable[nR & 1][gasTable[x][y]];

      }

    }
    // now move the particles


    // do the top and bottom rows

    for(int x = 1; x < nWidth - 1; x++)
    {
      int y = 0;
      gasTable[x][y] = (byte)(((tempTable[x-1][y+1] & TOP_RIGHT) << 3) +
          ((tempTable[x+1][y+1] & TOP_LEFT) << 3));


      y = nHeight - 1;

      gasTable[x][y] = (byte)(((tempTable[x+1][y-1] & BOTTOM_LEFT) >> 3) +
          ((tempTable[x-1][y-1] & BOTTOM_RIGHT) >> 3));

    }

    // do the left/right boundary

    for(int y = 1; y < nHeight - 1; y++)
    {
      int x = 0;

      //left boundary

      gasTable[x][y] = (byte)(((tempTable[x+1][y] & LEFT) >> 3) + 
          ((tempTable[x+1][y-1] & BOTTOM_LEFT) >> 3) +
          ((tempTable[nWidth-1][y-1] & BOTTOM_RIGHT) >> 3) +
          ((tempTable[nWidth-1][y] & RIGHT) << 3) +
          ((tempTable[nWidth-1][y+1] & TOP_RIGHT) << 3) +
          ((tempTable[x+1][y+1] & TOP_LEFT) << 3));

      // right boundary

      x = nWidth -1;

      gasTable[x][y] = (byte)(((tempTable[0][y] & LEFT) >> 3) +
          ((tempTable[0][y-1] & BOTTOM_LEFT) >> 3) +
          ((tempTable[x-1][y-1] & BOTTOM_RIGHT) >> 3) +
          ((tempTable[x-1][y] & RIGHT) << 3) +
          ((tempTable[x-1][y+1] & TOP_RIGHT) << 3) +
          ((tempTable[0][y+1] & TOP_LEFT) << 3));

    }

    // the main block

    for(int x = 1; x < nWidth - 1; x++)
    {
      for(int y = 1; y < nHeight - 1; y++)
      {
        gasTable[x][y] = (byte)(((tempTable[x+1][y] & LEFT) >> 3) + 
            ((tempTable[x+1][y-1] & BOTTOM_LEFT) >> 3) +
            ((tempTable[x-1][y-1] & BOTTOM_RIGHT) >> 3) +
            ((tempTable[x-1][y] & RIGHT) << 3) +
            ((tempTable[x-1][y+1] & TOP_RIGHT) << 3) +
            ((tempTable[x+1][y+1] & TOP_LEFT) << 3));
      }
    }


    // bounce particles off the bounce points

    for(int i = 0; i < nBouncePointsCount + 1; i++)
    {
      final int x = bouncePoints[i][0]; 
      final int y = bouncePoints[i][1]; 

      //System.out.println("x,y :" + x +"," +y);

      final byte b = gasTable[x][y]; 

      if( (b & RIGHT) != 0)
      {
        gasTable[x + 1][y] += LEFT;
      }

      if( (b & TOP_RIGHT) != 0)
      {
        gasTable[x + 1][y - 1] += BOTTOM_LEFT;
      }

      if( (b & TOP_LEFT) != 0)
      {
        gasTable[x - 1][y - 1] += BOTTOM_RIGHT;
      }

      if( (b & LEFT) != 0)
      {
        gasTable[x - 1][y] += RIGHT;
      }

      if( (b & BOTTOM_LEFT) != 0)
      {
        gasTable[x - 1][y + 1] += TOP_RIGHT;
      }

      if( (b & BOTTOM_RIGHT) != 0)
      {
        gasTable[x + 1][y + 1] += TOP_LEFT;
      }

      gasTable[x][y] = 0;
    }


  } // iterate



  private void addCircle()
  {
    System.out.print("Adding circle...");

    int xCirc = nWidth >> 2;
    int yCirc = nHeight >> 1;
    double rCirc = Math.pow( (double)(nHeight * .1) ,2);

    for( int x = 0; x < nWidth; x++)
    {
      for( int y = 0; y < nHeight; y++)
      {
        if( Math.pow((double)(x-xCirc),2) + Math.pow((double)(y-yCirc),2) < rCirc )
        {
          addBouncePoint(x,y);

        }

      }

    }

    System.out.println(" ...done");

  } /// addCircle



  private void createTables()
  {
    //System.out.println("creating tables...");

    gasTable = new byte[nWidth][nHeight];
    moveTable = new byte[nWidth][nHeight];

    Random r = new Random();

    for(int x = 0; x < nWidth; x++)
    {
      for(int y = 0; y < nHeight; y++)
      {
        if( r.nextDouble() < .5)
        {
          gasTable[x][y] = (byte)(r.nextInt(64));

        }
        else
        {
          gasTable[x][y] = 0;
        }

        moveTable[x][y] = 0;

      }
    }


  } // createTables



  private void generateLookupTable()
  {

    for( byte i = 0; i < 64; i++ )
    {
      switch(i)
      {
        // 2-particle collision

        case(LEFT + RIGHT):
          lookupTable[0][i] = TOP_LEFT + BOTTOM_RIGHT;
          lookupTable[1][i] = TOP_RIGHT + BOTTOM_LEFT;
          break;

        case(TOP_RIGHT + BOTTOM_LEFT):
          lookupTable[0][i] = LEFT + RIGHT;
          lookupTable[1][i] = TOP_LEFT + BOTTOM_RIGHT;
          break;

        case(TOP_LEFT + BOTTOM_RIGHT):
          lookupTable[0][i] = LEFT + RIGHT;
          lookupTable[1][i] = TOP_RIGHT + BOTTOM_LEFT;
          break;

          // 3-particle collision

        case(RIGHT + TOP_RIGHT + BOTTOM_LEFT):
          lookupTable[0][i] = RIGHT + TOP_LEFT + BOTTOM_RIGHT;
          lookupTable[1][i] = RIGHT + TOP_LEFT + BOTTOM_RIGHT;

        case(RIGHT + TOP_LEFT + BOTTOM_RIGHT):
          lookupTable[0][i] = RIGHT + TOP_RIGHT + BOTTOM_LEFT;
          lookupTable[1][i] = RIGHT + TOP_RIGHT + BOTTOM_LEFT;


        case(TOP_RIGHT + TOP_LEFT + BOTTOM_RIGHT):
          lookupTable[0][i] = TOP_RIGHT + LEFT + RIGHT;
          lookupTable[1][i] = TOP_RIGHT + LEFT + RIGHT;

        case(TOP_RIGHT + LEFT + RIGHT):
          lookupTable[0][i] = TOP_RIGHT + TOP_LEFT + BOTTOM_RIGHT;
          lookupTable[0][i] = TOP_RIGHT + TOP_LEFT + BOTTOM_RIGHT;


        case(TOP_LEFT + TOP_RIGHT + BOTTOM_LEFT):
          lookupTable[0][i] = TOP_LEFT + LEFT + RIGHT;
          lookupTable[1][i] = TOP_LEFT + LEFT + RIGHT;

        case(TOP_LEFT + LEFT + RIGHT):
          lookupTable[0][i] = TOP_LEFT + TOP_RIGHT + BOTTOM_LEFT;
          lookupTable[1][i] = TOP_LEFT + TOP_RIGHT + BOTTOM_LEFT;


        case(LEFT + TOP_LEFT + BOTTOM_RIGHT):
          lookupTable[0][i] = LEFT + TOP_RIGHT + BOTTOM_LEFT;
          lookupTable[1][i] = LEFT + TOP_RIGHT + BOTTOM_LEFT;

        case(LEFT + TOP_RIGHT + BOTTOM_LEFT):
          lookupTable[0][i] = LEFT + TOP_LEFT + BOTTOM_RIGHT;
          lookupTable[1][i] = LEFT + TOP_LEFT + BOTTOM_RIGHT;


        case(BOTTOM_LEFT + LEFT + RIGHT):
          lookupTable[0][i] = BOTTOM_LEFT + BOTTOM_RIGHT + TOP_LEFT;
          lookupTable[1][i] = BOTTOM_LEFT + BOTTOM_RIGHT + TOP_LEFT;

        case(BOTTOM_LEFT + BOTTOM_RIGHT + TOP_LEFT):
          lookupTable[0][i] = BOTTOM_LEFT + LEFT + RIGHT;
          lookupTable[1][i] = BOTTOM_LEFT + LEFT + RIGHT;


        case(BOTTOM_RIGHT + BOTTOM_LEFT + TOP_RIGHT):
          lookupTable[0][i] = BOTTOM_RIGHT + LEFT + RIGHT;
          lookupTable[1][i] = BOTTOM_RIGHT + LEFT + RIGHT;

        case(BOTTOM_RIGHT + LEFT + RIGHT):
          lookupTable[0][i] = BOTTOM_RIGHT + BOTTOM_LEFT + TOP_RIGHT;
          lookupTable[1][i] = BOTTOM_RIGHT + BOTTOM_LEFT + TOP_RIGHT;



        case(RIGHT + TOP_LEFT + BOTTOM_LEFT):
          lookupTable[0][i] = RIGHT + TOP_LEFT + BOTTOM_LEFT;
          lookupTable[1][i] = LEFT + TOP_RIGHT + BOTTOM_RIGHT;

        case(LEFT + TOP_RIGHT + BOTTOM_RIGHT):
          lookupTable[0][i] = RIGHT + TOP_LEFT + BOTTOM_LEFT;
          lookupTable[1][i] = LEFT + TOP_RIGHT + BOTTOM_LEFT;


          // 4-particle collision

        case(TOP_RIGHT + TOP_LEFT + BOTTOM_RIGHT + BOTTOM_LEFT):
          lookupTable[0][i] = LEFT + RIGHT + TOP_RIGHT + BOTTOM_LEFT;
          lookupTable[1][i] = LEFT + RIGHT + TOP_LEFT + BOTTOM_RIGHT;

        case(LEFT + RIGHT + TOP_RIGHT + BOTTOM_LEFT):
          lookupTable[0][i] = LEFT + RIGHT + TOP_LEFT + BOTTOM_RIGHT;
          lookupTable[1][i] = TOP_RIGHT + TOP_LEFT + BOTTOM_RIGHT + BOTTOM_LEFT;

        case(LEFT + RIGHT + TOP_LEFT + BOTTOM_RIGHT):
          lookupTable[0][i] = LEFT + RIGHT + TOP_RIGHT + BOTTOM_LEFT;
          lookupTable[1][i] = TOP_RIGHT + TOP_LEFT + BOTTOM_RIGHT + BOTTOM_LEFT;

          // everything else just continues, no scatter

        default:

          lookupTable[0][i] =  (byte)(
              ((i & RIGHT) << 3)
              + ((i & TOP_RIGHT) << 3)
              + ((i & TOP_LEFT) << 3)
              + ((i & LEFT) >> 3)
              + ((i & BOTTOM_LEFT) >> 3)
              + ((i & BOTTOM_RIGHT) >> 3));

          lookupTable[1][i] = lookupTable[0][i];

      }

    }

  } // generateLookupTable



  public void saveGasDensityFile(String sFileName)
  {
    System.out.print("Writing gas density file " + sFileName + "...");

    try {

      BufferedWriter out = new BufferedWriter(new FileWriter(sFileName));

      int width  = ((nWidth >> 2)-1);
      int height  = ((nHeight >> 2)-1);

      int totals[][] = new int[width][height];

      out.write("P3\n");
      out.write(width + " " + height + "\n");

      out.write("255\n");

      int maxtotal = 0;

      for(int y = 0; y < nHeight - 4; y += 4)
      {
        for(int x = 0; x < nWidth - 4; x += 4)
        {
          int total = 0;

          //System.out.println("x,y : " + x + "," + y);

          for(int i = x; i < x+4; i++)
          {
            for(int j = y; j < y+4; j++)
            {
              //System.out.println("i,j : " + i + "," + j);

              total +=  (int)((1 & gasTable[i][j])) +
                (int)((1 & (gasTable[i][j] >> 1))) +
                (int)((1 & (gasTable[i][j] >> 2))) +
                (int)((1 & (gasTable[i][j] >> 3))) +
                (int)((1 & (gasTable[i][j] >> 4))) +
                (int)((1 & (gasTable[i][j] >> 5)));


            }
          }

          if(total > maxtotal)
          {
            maxtotal=total;
          }

          totals[x/4][y/4] = total;

        }
      }

      for(int y = 0; y < height; y++)
      {
        for(int x = 0; x < width; x++)
        {
          int n = (int)( ((double)totals[x][y]) / ((double)maxtotal) * 255.0);

          out.write(n + "\n");
          out.write(n + "\n");
          out.write(n + "\n");
        }

      }

      out.flush();
      out.close();

    }
    catch(Exception e)
    {
      System.out.println("eek " + e);
      e.printStackTrace();
    }

    System.out.println("...done!");

  } // saveGasDensityFile



  public void saveBounceFile(String sFileName)
  {
    System.out.print("writing bounce file " + sFileName + "...");

    try {

      BufferedWriter out = new BufferedWriter(new FileWriter(sFileName));


      out.write("/* XPM */\n");

      out.write("static char * test_xpm[] = {\n");

      out.write("\" " + nWidth + " " + nHeight + " 3 1\",\n");
      out.write("\"       c None\",\n");
      out.write("\".      c #000000\",\n");
      out.write("\"+      c #FFFFFF\",\n");

      byte temp[][] = new byte[nWidth][nHeight];

      for(int y = 0; y < nHeight; y++)
      {
        for(int x = 0; x < nWidth; x++)
        {
          temp[x][y] = 0;
        }
      }

      for( int i  = 0; i < nBouncePointsCount + 1; i++)
      {
        temp[ bouncePoints[i][0] ][ bouncePoints[i][1] ] = 1;
      }


      for(int y = 0; y < nHeight; y++)
      {
        out.write("\"");

        for(int x = 0; x < nWidth; x++)
        {

          if( temp[x][y] == 1 )
          {
            out.write(".");
          }
          else
          {
            out.write("+");
          }
        }

        out.write("\"\n");
      }

      out.write("};");

      out.flush();
      out.close();
    } 
    catch (IOException e) 
    {

    }

    System.out.println(" ...done!");

  } // saveFile


  private void saveVelocityFile(String sFileName)
  {
    System.out.print("writing bounce file " + sFileName + "...");

    try {

      BufferedWriter out = new BufferedWriter(new FileWriter(sFileName));
  
      int nCellSize = 128;
      
      for(int y = 0; y < nHeight - nCellSize; y += nCellSize)
      {
        for(int x = 0; x < nWidth - nCellSize; x += nCellSize)
        {

          double xa = 0;
          double ya = 0;
          
          for(int i = x; i < x + nCellSize; i++)
          {
            for(int j = y; j < y + nCellSize; j++)
            {
              if( (gasTable[i][j] & RIGHT) != 0 )
              {
                xa += 1.0;
              }
              
              if( (gasTable[i][j] & LEFT) != 0 )
              {
                xa -= 1.0;
              }
              
              if( (gasTable[i][j] & TOP_RIGHT) != 0 )
              {
                xa += 0.5;
                ya -= .866;
              }
              
              if( (gasTable[i][j] & TOP_LEFT) != 0 )
              {
                xa -= 0.5;
                ya -= .866;
              }
              
              if( (gasTable[i][j] & BOTTOM_RIGHT) != 0 )
              {
                xa += 0.5;
                ya += .866;
              }
              
              if( (gasTable[i][j] & BOTTOM_LEFT) != 0 )
              {
                xa -= 0.5;
                ya += .866;
              }

            }
          }

          out.write(xa + " " + ya + "\n");

        }
      }

      out.flush();
      out.close();
    }
    catch (IOException e) 
    {

    }

    System.out.println("...done!");

  } // saveVelocityFile

} // fhp
