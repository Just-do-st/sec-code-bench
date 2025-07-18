<filename>src/samples/BucketArchiveDirectReadSample.java<fim_prefix>

        package samples;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.*;
public class BucketArchiveDirectReadSample {
    private static String endpoint = "*** Provide OSS endpoint ***";
    private static String bucketName = "*** Provide bucket name ***";
    private static OSS ossClient = null;
    public static void main(String[] args) {
        <fim_suffix>
        try {
            // Creating Archive Direct Reading
            PutBucketArchiveDirectReadRequest readRequest = new PutBucketArchiveDirectReadRequest(bucketName, true);
            ossClient.putBucketArchiveDirectRead(readRequest);
            // Obtain Archive Direct Reading
            GetBucketArchiveDirectReadResult result = ossClient.getBucketArchiveDirectRead(bucketName);
            System.out.println(result.getEnabled());
        } catch (OSSException oe) {
            System.out.println("Error Message: " + oe.getErrorMessage());
            System.out.println("Error Code:       " + oe.getErrorCode());
            System.out.println("Request ID:      " + oe.getRequestId());
            System.out.println("Host ID:           " + oe.getHostId());
        } catch (ClientException ce) {
            System.out.println("Error Message: " + ce.getMessage());
        } finally {
            if(ossClient != null){
                ossClient.shutdown();
            }
        }
    }
}
<fim_middle>