package com.kenyarealestate.review.exception;
import org.springframework.http.*; import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*; import java.time.LocalDateTime; import java.util.Map;
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RuntimeException.class) public ResponseEntity<Map<String,Object>> r(RuntimeException e){ return build(HttpStatus.BAD_REQUEST,e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<Map<String,Object>> v(MethodArgumentNotValidException e){
        String m=e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+": "+x.getDefaultMessage()).findFirst().orElse("Validation error");
        return build(HttpStatus.BAD_REQUEST,m);
    }
    private ResponseEntity<Map<String,Object>> build(HttpStatus s, String msg){ return ResponseEntity.status(s).body(Map.of("timestamp",LocalDateTime.now().toString(),"status",s.value(),"error",msg)); }
}
