LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE :=  yuv
LOCAL_SRC_FILES :=  $(LOCAL_PATH)/prebuilt/$(TARGET_ARCH_ABI)/libyuv.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := yuvconvert
LOCAL_SRC_FILES := yuvconvert.c
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
LOCAL_SHARED_LIBRARIES := yuv
include $(BUILD_SHARED_LIBRARY)