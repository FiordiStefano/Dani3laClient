#define CRCPP_USE_CPP11
#include <jni.h>
#include "danisync_client_FileCRCIndex.h"
#include"CRC.h"
#include<fstream>
#include<iostream>
#include<Windows.h>
#include<process.h>
#include<thread>
using namespace std;

struct buffCRC {
    unsigned int buff_size;
    unsigned char * buff;
    int index;
};

HANDLE hMtx = CreateMutex(NULL, false, NULL);
auto table = CRC::CRC_32().MakeTable();
jlong * digests;
unsigned supportedThreads = thread::hardware_concurrency();
unsigned runningThreads = 0;

void thCRC(void * data) {
    buffCRC * bData = (buffCRC *) data;
    digests[bData->index] = CRC::Calculate(bData->buff, bData->buff_size, table);
    free(bData->buff);
    free(bData);
    if (WaitForSingleObject(hMtx, INFINITE) == WAIT_OBJECT_0) {
        runningThreads--;
        ReleaseMutex(hMtx);
    }
}

/*
 * Class:     danisync_client_FileCRCIndex
 * Method:    calculateFileCRC
 * Signature: (Ljava/lang/String;IJ)[J
 */
JNIEXPORT jlongArray JNICALL Java_danisync_client_FileCRCIndex_calculateFileCRC
(JNIEnv *env, jobject thisObj, jstring inJNIStr, jint inJNIchunkSize, jlong inJNInChunks, jlong inJNIfileLength) {

    // Step 1: Convert the JNI String (jstring) into C-String (char*)
    const char *inCStr = (*env).GetStringUTFChars(inJNIStr, NULL);
    if (inCStr == NULL) return NULL;

    // Step 2: Perform its intended operations
    ifstream file(inCStr, ios::in | ios::binary);
    if (!file) return NULL;
    else {
        long long fl = inJNIfileLength;
        const unsigned int buff_size = inJNIchunkSize;
        unsigned char * buff = (unsigned char *) malloc(buff_size * sizeof (unsigned char));
        if (buff == NULL) return NULL;
        else {
            long long np = inJNInChunks;
            long long last;
            if (fl % buff_size == 0) {
                last = buff_size;
            } else {
                last = fl % buff_size;
            }
            const unsigned int last_buff = (unsigned int) last;
            digests = (jlong *) malloc((unsigned int) np * sizeof (jlong));
            buffCRC ** buffers = (buffCRC **) malloc((unsigned int) np * sizeof (buffCRC *));
            if (digests == NULL && buffers == NULL) return NULL;
            else {
                HANDLE * threads = (HANDLE *) malloc((unsigned int) np * sizeof (HANDLE));
                for (int i = 0; i < np; i++) {
                    if (i != np - 1) {
                        file.read((char *) buff, buff_size);
                        while (true) {
                            if (runningThreads < supportedThreads) {
                                buffers[i] = (buffCRC *) malloc(sizeof (buffCRC));
                                if (buffers[i] != NULL) {
                                    buffers[i]->buff = (unsigned char *) malloc(buff_size * sizeof (unsigned char));
                                    if (buffers[i]->buff != NULL) {
                                        memcpy(buffers[i]->buff, buff, buff_size);
                                        buffers[i]->buff_size = buff_size;
                                        buffers[i]->index = i;
                                        threads[i] = (HANDLE) _beginthread(thCRC, 0, buffers[i]);
                                        if (WaitForSingleObject(hMtx, INFINITE) == WAIT_OBJECT_0) {
                                            runningThreads++;
                                            ReleaseMutex(hMtx);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    } else {
                        free(buff);
                        buff = (unsigned char *) malloc(last_buff * sizeof (unsigned char));
                        if (buff == NULL) return NULL;
                        else {
                            file.read((char *) buff, last_buff);
                            while (true) {
                                if (runningThreads < supportedThreads) {
                                    buffers[i] = (buffCRC *) malloc(sizeof (buffCRC));
                                    if (buffers[i] != NULL) {
                                        buffers[i]->buff = (unsigned char *) malloc(last_buff * sizeof (unsigned char));
                                        if (buffers[i]->buff != NULL) {
                                            buffers[i]->buff_size = last_buff;
                                            memcpy(buffers[i]->buff, buff, last_buff);
                                            buffers[i]->index = i;
                                            threads[i] = (HANDLE) _beginthread(thCRC, 0, buffers[i]);
                                            if (WaitForSingleObject(hMtx, INFINITE) == WAIT_OBJECT_0) {
                                                runningThreads++;
                                                ReleaseMutex(hMtx);
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                for(int i = 0; i < np; i++)
                    WaitForSingleObject(threads[i], INFINITE);
                file.close();
                free(buff);
                free(buffers);
                free(threads);
                CloseHandle(hMtx);
            }
        }
    }


    // Step 3: Convert the C's Native jlong[] to JNI jlongarray, and return
    jlongArray outJNIArray = (*env).NewLongArray(inJNInChunks);
    if (NULL == outJNIArray) return NULL;
    (*env).SetLongArrayRegion(outJNIArray, 0, inJNInChunks, digests);
    return outJNIArray;
}
