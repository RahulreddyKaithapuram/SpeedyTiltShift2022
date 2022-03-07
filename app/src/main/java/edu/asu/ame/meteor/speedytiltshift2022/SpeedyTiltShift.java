package edu.asu.ame.meteor.speedytiltshift2022;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Author : Kaithapuram Rahul Reddy, Hemanth Gangaraju, Sai Divya Nallapaneni, Ramya Sree Basani
 *
 * This class holds the logic to perform Gaussian blur algorithm in JAVA and acts as an entry point for NEON and C++ implementations
 * */
public class SpeedyTiltShift {
    private static final Logger logger = Logger.getLogger(String.valueOf(SpeedyTiltShift.class));
    static {
        System.loadLibrary("native-lib");
    }


    /*Method to construct the gaussian kernel with the given sigma value*/
    private static double[] GaussianBlurKernel(double sigma){
        if(sigma < 0.6)
            return new double[0];
        double radius = Math.ceil(2*sigma); // calculate the radius of kernel

        int kernelSize = (int)(Math.ceil(radius)*2) + 1; // calculate kerb]nel size
        double[] kernelVector = new double[kernelSize];

        int wholeRadius = (int)Math.ceil(radius);
        double sigmaSquare = sigma * sigma;
        double twoPiSigmaSquare = 2 * Math.PI * sigmaSquare;
        double sqrtTwoPiSigmaSquare = Math.sqrt(twoPiSigmaSquare);

        double firstTerm = 1/(sqrtTwoPiSigmaSquare);

        for(int k=-wholeRadius ;k<=wholeRadius;k++){
            double secondTerm = -1* k*k/(2*sigmaSquare);
            double weight = Math.exp(secondTerm)*firstTerm;

            kernelVector[k+wholeRadius] = weight;  // creating a kernel vector with the said method
        }

        return kernelVector;
    }

    /*Method to calculate sigma*/
    private static double calculateSigma(int low, int high, int y, float sigma, boolean isFarSigma){
        return  sigma * (isFarSigma ? (high - y) : (y - low))/(high - low);  // calculate sigma values according to the pixel position
    }

    /*Method to perform vertical convolution with varying sigma for every row*/
    private static void VerticalConvolution(int start, int stop, int[] pixelsIn, int[] pixelsOut, int width, double[][] gaussianKernelVectors){
        int lowIndex = start/width;
        int highIndex = stop/width;
        for (int i=start; i<=start+width; i++){
            for(int j = i; j < stop; j=j+width) {
                float bluePixel = 0;
                float greenPixel = 0;           // creating duplicate pixel values to operate on
                float redPixel = 0;

                int count = -1;
                int pixelVal;
                double[] gaussianKernelVector = gaussianKernelVectors[j/width];   // construct gaussian kernel for the given input pixels

                if(gaussianKernelVector.length == 0){
                    pixelsOut[j] = pixelsIn[j];
                    continue;
                }
                int radius = gaussianKernelVector.length/2;
                int rangeToBeConvoluted = radius * width;

                for(int k = j - rangeToBeConvoluted; k <= j + rangeToBeConvoluted; k = k + width) {
                    count++;
                    try{
                        pixelVal = pixelsIn[k];            /// performing vertical convolution according to the formula -
                    }catch (ArrayIndexOutOfBoundsException a){     // q(y, x) = G(-r)*p(y-r, x), + ... + G(0)*p(y, x),+ ... + G(r)*p(y+r, x)
                        pixelVal = 0;
                    }

                    int blue = pixelVal & 0xff;
                    int green = (pixelVal >> 8) & 0xff;
                    int red = (pixelVal >> 16) & 0xff;


                    redPixel += (gaussianKernelVector[count] * red );
                    greenPixel += (gaussianKernelVector[count] * green );
                    bluePixel += (gaussianKernelVector[count] * blue );

                }

                int combinedAlpha = 0xff;
                int combinedRed = (int) redPixel;
                int combinedGreen = (int) greenPixel;
                int combinedBlue = (int) bluePixel;
                pixelsOut[j] = (combinedAlpha & 0xff) << 24 | (combinedRed & 0xff) << 16 | (combinedGreen & 0xff) << 8 | (combinedBlue & 0xff);
            }                                                       // combining the convoluted R,G,B value into the pixel value
        }
    }

    /*Method to perform horizontal convolution with varying sigma for every row*/
    private static void HorizontalConvolution(int start, int stop, int[] pixelsIn, int[] pixelsOut, int width, int totalPixels, double[][] gaussianKernelVectors){
        for(int i=start;i<stop;i=i+width){
            int pixelRight = i + width - 1;
            double[] gaussianKernelVector = gaussianKernelVectors[i/width];

            if(gaussianKernelVector.length == 0){
                if (pixelRight - i >= 0)
                    System.arraycopy(pixelsIn, i, pixelsOut, i, pixelRight - i+1);
                continue;
            }

            int radius = gaussianKernelVector.length/2;
            for(int j = i; j <= pixelRight; j++) {
                float bluePixel = 0;
                float greenPixel = 0;
                float redPixel = 0;

                int pixelVal;

                for(int k = -radius; k <= radius; k++) {
                    int pixelIndex = j + k;                                                     /// performing vertical convolution according to the formula -
                    int vectorIndex = k + radius;                                               ///P(y, x) = G(-r)*q(y, x-r), + ... + G(0)*q(y, x),+ ... + G(r)*q(y, x+r)

                    if(pixelIndex >= 0 && pixelIndex < pixelRight && pixelIndex < totalPixels){
                        pixelVal = pixelsIn[pixelIndex];
                    }else{
                        pixelVal = 0;
                    }

                   /* try{
                        pixelVal = pixelsIn[k];
                    }catch (ArrayIndexOutOfBoundsException a){
                        pixelVal = 0;
                    }*/

                    int blue = pixelVal & 0xff;
                    int green = (pixelVal >> 8) & 0xff;
                    int red = (pixelVal >> 16) & 0xff;

                    redPixel += (gaussianKernelVector[vectorIndex] * red);
                    greenPixel += (gaussianKernelVector[vectorIndex] * green);
                    bluePixel += (gaussianKernelVector[vectorIndex] * blue);
                }

                int combinedAlpha = 0xff;
                int combinedRed = (int) redPixel;
                int combinedGreen = (int) greenPixel;
                int combinedBlue = (int) bluePixel;
                pixelsOut[j] = (combinedAlpha & 0xff) << 24 | (combinedRed & 0xff) << 16 | (combinedGreen & 0xff) << 8 | (combinedBlue & 0xff);
            }
        }
    }

    /*Method to perform vertical convolution with constant sigma for every row*/
    private static void VerticalConvolutionWithSigma(int start, int stop, int[] pixelsIn, int[] pixelsOut, int width, int totalPixels, int radius, double[] gaussianKernelVector){
        for (int i=start; i<start+width; i++){
            for(int j = i; j < stop; j=j+width) {
                float bluePixel = 0;
                float greenPixel = 0;
                float redPixel = 0;

                int count = -1;
                int pixelVal;

                int rangeToBeConvoluted = radius * width;
                for(int k = j - rangeToBeConvoluted; k <= j + rangeToBeConvoluted; k = k + width) {
                    count++;
                    try{
                        pixelVal = pixelsIn[k];
                    }catch (ArrayIndexOutOfBoundsException a){
                        pixelVal = 0;
                    }

                    int blue = pixelVal & 0xff;
                    int green = (pixelVal >> 8) & 0xff;
                    int red = (pixelVal >> 16) & 0xff;


                    redPixel += (gaussianKernelVector[count] * red );
                    greenPixel += (gaussianKernelVector[count] * green );
                    bluePixel += (gaussianKernelVector[count] * blue );
                }

                int combinedAlpha = 0xff;
                int combinedRed = (int) redPixel;
                int combinedGreen = (int) greenPixel;
                int combinedBlue = (int) bluePixel;

                pixelsOut[j] = (combinedAlpha & 0xff) << 24 | (combinedRed & 0xff) << 16 | (combinedGreen & 0xff) << 8 | (combinedBlue & 0xff);
            }
        }
    }

    /*Method to perform horizontal convolution with constant sigma for every row*/
    private static void HorizontalConvolutionWithSigma(int start, int stop, int[] pixelsIn, int[] pixelsOut, int width, int totalPixels, int radius, double[] gaussianKernelVector){
        for(int i=start;i<stop;i=i+width){
            int pixelRight = i + width;
            for(int j = i; j < pixelRight; j++) {
                float bluePixel = 0;
                float greenPixel = 0;
                float redPixel = 0;

                int pixelVal;

                for(int k = -radius; k <= radius; k++) {
                    int pixelIndex = j + k;
                    int vectorIndex = k + radius;
                    if(pixelIndex >= 0 && pixelIndex < pixelRight && pixelIndex < totalPixels){
                        pixelVal = pixelsIn[pixelIndex];
                    }else{
                        pixelVal = 0;
                    }

                    int blue = pixelVal & 0xff;
                    int green = (pixelVal >> 8) & 0xff;
                    int red = (pixelVal >> 16) & 0xff;

                    /*Gathering specific channels after processing with gaussian value*/
                    redPixel += (gaussianKernelVector[vectorIndex] * red);
                    greenPixel += (gaussianKernelVector[vectorIndex] * green);
                    bluePixel += (gaussianKernelVector[vectorIndex] * blue);
                }

                int combinedAlpha = 0xff;
                int combinedRed = (int) redPixel;
                int combinedGreen = (int) greenPixel;
                int combinedBlue = (int) bluePixel;
                pixelsOut[j] = (combinedAlpha & 0xff) << 24 | (combinedRed & 0xff) << 16 | (combinedGreen & 0xff) << 8 | (combinedBlue & 0xff);
            }
        }
    }

    /*Classify and call the methods based on the region of choice
     * @isSigmaFar - whether the given image region is far or near
     * @isSingleSigma - whether the given image pixels have single of varying sigma
     * */
    private static void Convolution(int start, int stop, int[] pixelsIn, int[] pixelsIntermediate, int[] pixelsOut, int height, int width, int totalPixels, float sigma, boolean isSigmaFar, boolean singleSigma){
        if(singleSigma){
            double[] gaussianKernelVector = GaussianBlurKernel(sigma);
            VerticalConvolutionWithSigma(start, stop, pixelsIn, pixelsIntermediate, width, totalPixels, gaussianKernelVector.length/2,gaussianKernelVector);
            HorizontalConvolutionWithSigma(start, stop, pixelsIntermediate, pixelsOut, width, totalPixels, gaussianKernelVector.length/2,gaussianKernelVector);
        }else {
            double[][] gaussianKernelVector = new double[height+1][];

            int lowIndex = start/width;
            int highIndex = stop/width;

            /*Pre calculating the gaussian vector for every row*/
            for(int i=lowIndex;i<=highIndex;i++){
                gaussianKernelVector[i] = GaussianBlurKernel(calculateSigma(lowIndex,highIndex,i,sigma,isSigmaFar));
            }

            VerticalConvolution(start, stop, pixelsIn, pixelsIntermediate,width,gaussianKernelVector);
            HorizontalConvolution(start, stop, pixelsIntermediate, pixelsOut, width, totalPixels, gaussianKernelVector);
        }
    }


    public static long Java_time_measure = 0;

    public static Bitmap tiltshift_java(Bitmap input, float sigma_far, float sigma_near, int a0, int a1, int a2, int a3){
        Bitmap outBmp = Bitmap.createBitmap(input.getWidth(), input.getHeight(), Bitmap.Config.ARGB_8888);
        //cannot write to input Bitmap, since it may be immutable
        //if you try, you may get a java.lang.IllegalStateException

        /*int[] pixels = new int[input.getHeight()*input.getWidth()];
        int[] pixelsOut = new int[input.getHeight()*input.getWidth()];
        input.getPixels(pixels,0,input.getWidth(),0,0,input.getWidth(),input.getHeight());
        for (int i=0; i<input.getWidth()*input.getHeight(); i++){
            int B = pixels[i]%0x100;
            int G = (pixels[i]>>8)%0x100;
            int R = (pixels[i]>>16)%0x100;
            int A = 0xff;



            int color = (A & 0xff) << 24 | (R & 0xff) << 16 | (G & 0xff) << 8 | (B & 0xff);
            pixelsOut[i]=color;
        }*/

        /* Image dimensions */
        final int imageWidth = input.getWidth();
        final int imageHeight = input.getHeight();

        final int totalPixels = input.getWidth()*input.getHeight();

        /* Arrays to keep track of image pixels */
        final int[] pixelsIn = new int[totalPixels];
        final int[] pixelsIntermediate  = new int[totalPixels];
        final int[] pixelsOut = new int[totalPixels];

        input.getPixels(pixelsIn,0,input.getWidth(),0,0,input.getWidth(),input.getHeight());
        long startTime = System.currentTimeMillis();
        /* 0-a0 section Sigma1 blur*/
        Convolution(0,(a0)*imageWidth,pixelsIn,pixelsIntermediate,pixelsOut,imageHeight,imageWidth,totalPixels,sigma_far,true, true);
        /* a0-a1 section Variable Sigma Blur*/
        Convolution((a0)*imageWidth,(a1)*imageWidth,pixelsIn,pixelsIntermediate,pixelsOut,imageHeight,imageWidth,totalPixels,sigma_far,true,false);
        /* a1-a2 section - No Blur */
        if(((a2)*imageWidth - (a1)*imageWidth)>=0)
            System.arraycopy(pixelsIn,(a1)*imageWidth,pixelsOut,(a1)*imageWidth,(a2)*imageWidth-(a1)*imageWidth);
        /* a2-a3 section - Variable blur*/
        Convolution((a2)*imageWidth,(a3)*imageWidth,pixelsIn,pixelsIntermediate,pixelsOut,imageHeight,imageWidth,totalPixels,sigma_near,false,false);

        /* a3-end section - Sigma2 Blur*/
        Convolution((a3)*imageWidth,imageHeight*imageWidth,pixelsIn,pixelsIntermediate,pixelsOut,imageHeight,imageWidth,totalPixels,sigma_near,false,true);
        long stopTime = System.currentTimeMillis(); // stop time count for execution
        Java_time_measure = stopTime - startTime;
        logger.log(Level.INFO,"Execution Time Java- "+Java_time_measure); // display execution time in run log file

        outBmp.setPixels(pixelsOut,0,input.getWidth(),0,0,input.getWidth(),input.getHeight()); // transform pixel values to bitmap

        return outBmp;
    }
    public static long Cpp_time_measure = 0;

    public static Bitmap tiltshift_cpp(Bitmap input, float sigma_far, float sigma_near, int a0, int a1, int a2, int a3){
        Bitmap outBmp = Bitmap.createBitmap(input.getWidth(), input.getHeight(), Bitmap.Config.ARGB_8888);
        /* Image dimensions */
        int[] pixels = new int[input.getHeight()*input.getWidth()];
        int[] pixelsOut = new int[input.getHeight()*input.getWidth()];
        input.getPixels(pixels,0,input.getWidth(),0,0,input.getWidth(),input.getHeight());
        long startTimeCpp = System.currentTimeMillis();
        // call the Cppnative function in native-lib.cpp file
        tiltshiftcppnative(pixels,pixelsOut,input.getWidth(),input.getHeight(),sigma_far,sigma_near,a0,a1,a2,a3);
        long stopTimeCpp = System.currentTimeMillis();
        Cpp_time_measure = stopTimeCpp - startTimeCpp ;
        logger.log(Level.INFO,"Execution Time CPP- "+Cpp_time_measure);
        outBmp.setPixels(pixelsOut,0,input.getWidth(),0,0,input.getWidth(),input.getHeight());
        return outBmp;

    }
    public static long Neon_time_measure = 0;

    public static Bitmap tiltshift_neon(Bitmap input, float sigma_far, float sigma_near, int a0, int a1, int a2, int a3){
        Bitmap outBmp = Bitmap.createBitmap(input.getWidth(), input.getHeight(), Bitmap.Config.ARGB_8888);
        /* Image dimensions */
        int[] pixels = new int[input.getHeight()*input.getWidth()];
        int[] pixelsOut = new int[input.getHeight()*input.getWidth()];
        input.getPixels(pixels,0,input.getWidth(),0,0,input.getWidth(),input.getHeight());
        long startTimeNeon = System.currentTimeMillis();
        // call the Neonnative function in native-lib.cpp file
        tiltshiftneonnative(pixels,pixelsOut,input.getWidth(),input.getHeight(),sigma_far,sigma_near,a0,a1,a2,a3);
        long stopTimeNeon = System.currentTimeMillis();
        Neon_time_measure = stopTimeNeon - startTimeNeon ;
        logger.log(Level.INFO,"Execution Time Neon- "+Neon_time_measure);
        outBmp.setPixels(pixelsOut,0,input.getWidth(),0,0,input.getWidth(),input.getHeight());
        return outBmp;




    }


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public static native int tiltshiftcppnative(int[] inputPixels, int[] outputPixels, int width, int height, float sigma_far, float sigma_near, int a0, int a1, int a2, int a3);
    public static native int tiltshiftneonnative(int[] inputPixels, int[] outputPixels, int width, int height, float sigma_far, float sigma_near, int a0, int a1, int a2, int a3);

}

