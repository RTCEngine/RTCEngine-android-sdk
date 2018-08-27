
#include <jni.h>
#include <string.h>

#include "libyuv.h"
#include "libyuv/convert_from.h"


JNIEXPORT void JNICALL Java_cc_dot_rtcengine_utils_yuvconvert_nativeNV12ToNV21
(JNIEnv * env, jobject thiz, jbyteArray j_src_buffer,jint width, jint height) {


    size_t src_size = (*env)->GetArrayLength( env, j_src_buffer );
    char tmp;
    unsigned char *src =  (unsigned char*)(*env)->GetByteArrayElements(env,j_src_buffer, 0);
    int i;
    for ( i = width * height; i < src_size; i += 2) {
        tmp = src[i];
        src[i] = src[i+1];
        src[i+1] = tmp;
    }
    (*env)->ReleaseByteArrayElements(env,j_src_buffer,src,JNI_ABORT);
	return;
}


JNIEXPORT void JNICALL Java_cc_dot_rtcengine_utils_yuvconvert_nativeI420ToNV21
(JNIEnv * env, jobject thiz, jbyteArray j_src_buffer,jint width, jint height,jbyteArray j_dst_buffer) {


    size_t src_size = (*env)->GetArrayLength( env, j_src_buffer );
    size_t dst_size = (*env)->GetArrayLength( env, j_dst_buffer );
    int src_stride = width;
    int dst_stride = width;

    unsigned char  *src = (unsigned char*)(*env)->GetByteArrayElements(env, j_src_buffer, 0);
    unsigned char *dst = (unsigned char*)(*env)->GetByteArrayElements(env, j_dst_buffer, 0);

    char* src_y = src;
    size_t src_stride_y = src_stride;
    char* src_u = src + src_stride * height;
    size_t src_stride_u = src_stride / 2;
    char* src_v = src + src_stride * height * 5 / 4;
    size_t src_stride_v = src_stride / 2;

    char* dst_y = dst;
    size_t dst_stride_y = dst_stride;
    size_t dst_stride_uv = dst_stride;
    char* dst_uv = dst + dst_stride * height;

    int ret = I420ToNV21(src_y, src_stride_y, src_u, src_stride_u, src_v,
                               src_stride_v, dst_y, dst_stride_y, dst_uv,
                               dst_stride_uv, width, height);

    (*env)->ReleaseByteArrayElements(env,j_src_buffer,src,JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env,j_dst_buffer,dst,JNI_ABORT);
}




JNIEXPORT void JNICALL Java_cc_dot_rtcengine_utils_yuvconvert_nativeARGBToNV21
(JNIEnv * env, jobject thiz, jbyteArray j_src_buffer,jint width, jint height,jbyteArray j_dst_buffer) {

    int src_stride = width;
    int dst_stride = width;


    unsigned char  *src = (unsigned char*)(*env)->GetByteArrayElements(env, j_src_buffer, 0);
    unsigned char *dst = (unsigned char*)(*env)->GetByteArrayElements(env, j_dst_buffer, 0);


    ARGBToNV21(src,width*4,
                    dst,width,
                    dst + width * height,width,
                    width,height);



    (*env)->ReleaseByteArrayElements(env,j_src_buffer,src,JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env,j_dst_buffer,dst,JNI_ABORT);
}



JNIEXPORT void JNICALL Java_cc_dot_rtcengine_utils_yuvconvert_nativeRGBAToNV21
(JNIEnv * env, jobject thiz, jbyteArray j_src_buffer,jint width, jint height,jbyteArray j_dst_buffer) {

    int src_stride = width;
    int dst_stride = width;



    unsigned char  *src = (unsigned char*)(*env)->GetByteArrayElements(env, j_src_buffer, 0);
    unsigned char *dst = (unsigned char*)(*env)->GetByteArrayElements(env, j_dst_buffer, 0);

    size_t src_size = width * height * 4;

    jbyteArray argbarrs = (*env)->NewByteArray(env,src_size);

    unsigned char  *srcm = (unsigned char*)(*env)->GetByteArrayElements(env, argbarrs, 0);

    RGBAToARGB(src,width * 4,
                    srcm,width * 4,
                    width, height);

    ARGBToNV21(srcm,width*4,
                    dst,width,
                    dst + width * height,width,
                    width,height);

    (*env)->DeleteLocalRef(env,argbarrs);

    (*env)->ReleaseByteArrayElements(env,j_src_buffer,src,JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env,j_dst_buffer,dst,JNI_ABORT);

}



JNIEXPORT void JNICALL Java_cc_dot_rtcengine_utils_yuvconvert_nativeNV21ToARGB
(JNIEnv * env, jobject thiz, jbyteArray j_src_buffer,jint width, jint height,jintArray j_dst_buffer) {

    unsigned char *src = (unsigned char*)(*env)->GetByteArrayElements(env, j_src_buffer, 0);
    unsigned int *dst = (unsigned int*)(*env)->GetIntArrayElements(env, j_dst_buffer, 0);


	int frameSize = width * height;

	int i = 0, j = 0,yp = 0;
	int uvp = 0, u = 0, v = 0;
	int y1192 = 0, r = 0, g = 0, b = 0;
	unsigned int *target=dst;
	for (j = 0, yp = 0; j < height; j++)
	{
		uvp = frameSize + (j >> 1) * width;
		u = 0;
		v = 0;
		for (i = 0; i < width; i++, yp++)
		{
			int y = (0xff & ((int) src[yp])) - 16;
			if (y < 0)
				y = 0;
			if ((i & 1) == 0)
			{
				v = (0xff & src[uvp++]) - 128;
				u = (0xff & src[uvp++]) - 128;
			}

			y1192 = 1192 * y;
			r = (y1192 + 1634 * v);
			g = (y1192 - 833 * v - 400 * u);
			b = (y1192 + 2066 * u);

			if (r < 0) r = 0; else if (r > 262143) r = 262143;
			if (g < 0) g = 0; else if (g > 262143) g = 262143;
			if (b < 0) b = 0; else if (b > 262143) b = 262143;
			target[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
		}
	}

    (*env)->ReleaseByteArrayElements(env,j_src_buffer,src,JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env,j_dst_buffer,dst,JNI_ABORT);

}





JNIEXPORT void JNICALL Java_cc_dot_rtcengine_utils_yuvconvert_nativeI420ToARGB
(JNIEnv * env, jobject thiz, jbyteArray j_src_buffer,jint width, jint height,jbyteArray j_dst_buffer) {

    int src_stride = width;

    unsigned char  *src = (unsigned char*)(*env)->GetByteArrayElements(env, j_src_buffer, 0);
    unsigned char  *dst = (unsigned char*)(*env)->GetByteArrayElements(env, j_dst_buffer, 0);

    char* src_y = src;
    size_t src_stride_y = src_stride;
    char* src_u = src + src_stride * height;
    size_t src_stride_u = src_stride / 2;
    char* src_v = src + src_stride * height * 5 / 4;
    size_t src_stride_v = src_stride / 2;


    I420ToARGB(src_y, src_stride_y,
               src_u, src_stride_u,
               src_v,src_stride_v,
               dst, width * 4,
               width, height);


    (*env)->ReleaseByteArrayElements(env,j_src_buffer,src,JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env,j_dst_buffer,dst,JNI_ABORT);

}




