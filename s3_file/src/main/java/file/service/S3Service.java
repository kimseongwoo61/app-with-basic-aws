package file.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import file.entity.AttachmentFile;
import file.repository.AttachmentFileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class S3Service {

    private final AmazonS3 amazonS3;
    private final AttachmentFileRepository fileRepository;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    private final String DIR_NAME = "s3_data";

    @Transactional
    public void uploadS3File(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new Exception("파일이 전달되지 않았습니다.");
        }

        String tmpPath = System.getProperty("java.io.tmpdir");
        String uniqueName = UUID.randomUUID().toString();
        File tmpFile = new File(tmpPath, uniqueName);

        file.transferTo(tmpFile);

        String s3FileName = DIR_NAME + "/" + uniqueName;
        amazonS3.putObject(new PutObjectRequest(bucketName, s3FileName, tmpFile));

        // DB 저장
        AttachmentFile attachment = AttachmentFile.builder()
                .attachmentFileName(s3FileName)
                .attachmentOriginalFileName(file.getOriginalFilename())
                .attachmentFileSize(file.getSize())
                .filePath(bucketName + "/" + s3FileName)
                .build();

        fileRepository.save(attachment);

        // 임시 파일 삭제
        if (!tmpFile.delete()) {
            System.out.println("임시 파일 삭제 실패: " + tmpFile.getAbsolutePath());
        }
    }


    @Transactional
    public ResponseEntity<Resource> downloadS3File(Long fileId) {
        AttachmentFile attachmentFile = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("파일이 존재하지 않습니다."));

        String key = attachmentFile.getFilePath();
        S3Object s3Object = amazonS3.getObject(bucketName, key);
        S3ObjectInputStream inputStream = s3Object.getObjectContent();
        Resource resource = new InputStreamResource(inputStream);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition
                .builder("attachment")
                .filename(attachmentFile.getAttachmentOriginalFileName())
                .build());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }
}
