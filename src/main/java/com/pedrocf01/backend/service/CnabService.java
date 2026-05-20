package com.pedrocf01.backend.service;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class CnabService {
    private final Path fileStorageLocation;
    private final JobOperator jobOperator;
    private final Job job;

    public CnabService(@Value("${file.upload-dir}") String fileStorageLocation,
                       @Qualifier("jobOperatorAsync") JobOperator jobOperator, Job job) {
        this.fileStorageLocation = Paths.get(fileStorageLocation);
        this.jobOperator = jobOperator;
        this.job = job;
    }

    public void uploadCnabFile(MultipartFile file) throws Exception {
        var fileName = StringUtils.cleanPath(file.getOriginalFilename());
        var targetLocation = fileStorageLocation.resolve(fileName);
        file.transferTo(targetLocation);

        var jobParameters = new JobParametersBuilder().addJobParameter("cnab", file.getOriginalFilename(),
                                                                        String.class, true)
                                                      .addJobParameter("cnabFile",
                                                              "file:" + targetLocation.toString(),
                                                                        String.class, false)
                                                      .toJobParameters();

        jobOperator.start(job, jobParameters);
    }
}
