/**
 * Amazon Kinesis Aggregators Copyright 2014, Amazon.com, Inc. or its
 * affiliates. All Rights Reserved. Licensed under the Amazon Software License
 * (the "License"). You may not use this file except in compliance with the
 * License. A copy of the License is located at http://aws.amazon.com/asl/ or in
 * the "license" file accompanying this file. This file is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.amazonaws.services.kinesis.scaling.auto;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Transfer Object for the Autoscaling Configuration, which can be built from a
 * variety of file locations
 */
public class AutoscalingConfiguration implements Serializable {
    private static final Log LOG = LogFactory.getLog(AutoscalingConfiguration.class);

    private static ObjectMapper mapper = new ObjectMapper();

    private String streamName, region, scaleOnOperation;

    private ScalingConfig scaleUp;

    private ScalingConfig scaleDown;

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getScaleOnOperation() {
        return scaleOnOperation;
    }

    public void setScaleOnOperation(String scaleOnOperation) throws Exception {
        switch (scaleOnOperation) {
            case "PUT":
            case "GET":
                break;
            default:
                throw new Exception("Invalid Scaling Operation Type to Monitor");
        }
        this.scaleOnOperation = scaleOnOperation;
    }

    public ScalingConfig getScaleUp() {
        return scaleUp;
    }

    public void setScaleUp(ScalingConfig scaleUp) {
        this.scaleUp = scaleUp;
    }

    public ScalingConfig getScaleDown() {
        return scaleDown;
    }

    public void setScaleDown(ScalingConfig scaleDown) {
        this.scaleDown = scaleDown;
    }

    public static AutoscalingConfiguration[] loadFromURL(String url) throws IOException {
        File configFile = null;

        if (url.startsWith("s3://")) {
            // download the configuratino from S3
            AmazonS3 s3Client = new AmazonS3Client(new DefaultAWSCredentialsProviderChain());

            TransferManager tm = new TransferManager(s3Client);

            // parse the config path to get the bucket name and prefix
            final String s3ProtoRegex = "s3:\\/\\/";
            String bucket = url.replaceAll(s3ProtoRegex, "").split("/")[0];
            String prefix = url.replaceAll(String.format("%s%s\\/", s3ProtoRegex, bucket), "");

            // download the file using TransferManager
            configFile = File.createTempFile(url, null);
            Download download = tm.download(bucket, prefix, configFile);
            try {
                download.waitForCompletion();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }

            // shut down the transfer manager
            tm.shutdownNow();

            LOG.info(String.format("Loaded Configuration from Amazon S3 %s/%s to %s", bucket,
                    prefix, configFile.getAbsolutePath()));
        } else {
            configFile = File.createTempFile("kinesis-autoscaling-config", null);
            FileUtils.copyURLToFile(new URL(url), configFile, 1000, 1000);
            LOG.info(String.format("Loaded Configuration from %s to %s", url,
                    configFile.getAbsolutePath()));
        }

        // read the json config into an array of autoscaling
        // configurations
        AutoscalingConfiguration[] configuration = mapper.readValue(configFile,
                AutoscalingConfiguration[].class);

        return configuration;
    }
}
