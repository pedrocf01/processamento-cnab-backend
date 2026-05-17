package com.pedrocf01.backend.web;

import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ExceptionHandlerController {

    @ExceptionHandler(JobInstanceAlreadyCompleteException.class)
    private ResponseEntity<Object> handleFileAlrealdyImported(JobInstanceAlreadyCompleteException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                             .body("O arquivo informado já foi importado no sistema!");
    }
}
