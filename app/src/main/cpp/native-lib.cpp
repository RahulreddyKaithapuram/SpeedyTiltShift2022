#include <jni.h>
#include <string>
#include <cpu-features.h>
#include <android/log.h>
#include <arm_neon.h>
#include <chrono>
#include "Convolution_cpp.h"
#include "Convolution_neon.h"

using namespace std::chrono;

extern "C"
JNIEXPORT jint JNICALL
Java_edu_asu_ame_meteor_speedytiltshift2022_SpeedyTiltShift_tiltshiftcppnative(JNIEnv *env,
                                                                               jclass instance, jintArray inputPixels_, jintArray outputPixels_, jint width,
                                                                               jint height, jfloat sigma_far, jfloat sigma_near, jint a0, jint a1, jint a2, jint a3) {
    /* Arrays to keep track of image pixels */
    jint *pixels = env->GetIntArrayElements(inputPixels_, NULL);
    jint *outputPixels = env->GetIntArrayElements(outputPixels_, NULL);
    jint *pixelsIntermediate = env->GetIntArrayElements(outputPixels_, NULL);

    int totalPixels = width*height;
    /*
    __android_log_print(ANDROID_LOG_DEBUG, "a0", "a0:%d  a1:%d  a2:%d   a3:%d   sigma_far:%f  sigma_near:%f", a0, a1, a2, a3, sigma_far, sigma_near);
    */

    auto start = high_resolution_clock::now();  // start clock
    /* 0-a0 section Sigma1 blur*/
    performConvolution(0,(a0)*width,pixels,pixelsIntermediate,outputPixels,width,totalPixels,sigma_far,true,true);
    /* a0-a1 section Variable Sigma Blur*/
    performConvolution((a0)*width,(a1)*width,pixels,pixelsIntermediate,outputPixels,width,totalPixels,sigma_far,true,false);
    /* a1-a2 section - No Blur */
    for(int k=((a1)*width);k<=((a2)*width);k++)
    {
        outputPixels[k] = pixels[k];
    }
    /* a2-a3 section - Variable blur*/
    performConvolution((a2)*width,(a3)*width,pixels,pixelsIntermediate,outputPixels,width,totalPixels,sigma_near,false,false);
    /* a3-end section - Sigma2 Blur*/
    performConvolution((a3)*width,height*width,pixels,pixelsIntermediate,outputPixels,width,totalPixels,sigma_far,false,true);

    auto stop = high_resolution_clock::now(); // stop Clock

    auto cppDuration = duration_cast<microseconds>(stop - start);
    __android_log_print(ANDROID_LOG_INFO, "Execution time - CPP (microseconds)", "%lld", cppDuration); // display time in run log file
    env->ReleaseIntArrayElements(inputPixels_, pixels, 0);
    env->ReleaseIntArrayElements(outputPixels_, outputPixels, 0); // release the memory allocated to input and output pixel arrays
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_edu_asu_ame_meteor_speedytiltshift2022_SpeedyTiltShift_tiltshiftneonnative(JNIEnv *env, jclass instance, jintArray inputPixels_, jintArray outputPixels_, jint width,
                                                                                jint height, jfloat sigma_far, jfloat sigma_near, jint a0, jint a1,jint a2, jint a3) {

    /* Arrays to keep track of image pixels */
    jint *pixels = env->GetIntArrayElements(inputPixels_, NULL);
    jint *outputPixels = env->GetIntArrayElements(outputPixels_, NULL);
    jint *pixelsIntermediate = env->GetIntArrayElements(outputPixels_, NULL);
    /*
    __android_log_print(ANDROID_LOG_DEBUG, "a0", "a0:%d  a1:%d  a2:%d   a3:%d   sigma_far:%f  sigma_near:%f", a0, a1, a2, a3, sigma_far, sigma_near);
    */

    uint8_t * arrayInPtr = (uint8_t *)pixels;  //creating array pointers for input and output pixels
    uint8_t * arrayOutPtr = (uint8_t *)outputPixels;

    int totalPixels  = width*height;
    uint8x16x4_t pixelChannels;

    auto start = high_resolution_clock::now();// start clock
    /* 0-a0 section Sigma1 blur*/
    performConvolutionNeon(0,(a0)*width,pixels,pixelsIntermediate,outputPixels,width,totalPixels,sigma_far,true,true);
    /* a0-a1 section Variable Sigma Blur*/
    performConvolutionNeon((a0)*width,(a1)*width,pixels,pixelsIntermediate,outputPixels,width,totalPixels,sigma_far,true,false);
    /* a1-a2 section - No Blur*/
    int rangeVal = ((a2-a1)*width)/16;
    arrayInPtr = arrayInPtr + (a1*width*4);
    arrayOutPtr = arrayOutPtr + (a1*width*4);
    for(int i=0;i<rangeVal;i++){
        pixelChannels = vld4q_u8(arrayInPtr);  // method to copy array ( duplicate an array in Neon)
        vst4q_u8(arrayOutPtr,pixelChannels);
        arrayOutPtr = arrayOutPtr+64;
        arrayInPtr = arrayInPtr+64;
    }
    /* a2-a3 section - Variable blur*/
    performConvolutionNeon((a2)*width,(a3)*width,pixels,pixelsIntermediate,outputPixels,width,totalPixels,sigma_near,false,false);
    /* a3-end section - Sigma2 Blur*/
    performConvolutionNeon((a3)*width,height*width,pixels,pixelsIntermediate,outputPixels,width,totalPixels,sigma_far,false,true);


    auto stop = high_resolution_clock::now();

    /*Clock duration in micro seconds*/
    auto neonDuration = duration_cast<microseconds>(stop - start);
    __android_log_print(ANDROID_LOG_INFO, "Execution time - NEON (microseconds)", "%lld", neonDuration);

    env->ReleaseIntArrayElements(inputPixels_, pixels, 0);
    env->ReleaseIntArrayElements(outputPixels_, outputPixels, 0);
    return 0;
}
