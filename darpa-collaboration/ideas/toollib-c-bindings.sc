(MODULE TOOLLIB-C-BINDINGS)

(include "QobiScheme.sch")
(include "toollib-c-macros.sch")
(include "toollib-c-bindings.sch")

(c-include "idealib-c.h")
(c-include "stdlib.h")
(c-include "stdio.h")
(c-include "sys/types.h")
(c-include "unistd.h")
(c-include "gmp.h")

(define (curry2 f)
  (lambda (x) (lambda (y) (f x y))))

(define malloc (c-function pointer ("malloc" int)))
(define memcpy (c-function pointer ("memcpy" pointer pointer int)))
(define bzero (c-function void ("bzero" pointer int)))
(define free (c-function void ("free" pointer)))
(define (with-alloc x f)
  (let* ((data (malloc x))
	 (r (begin (bzero data x) (f data))))
    (free data)
    r))

(define (symbol->c-string s)
 (pregexp-replace*
  "-"
  (string-downcase (symbol->string s))
  "_"))

(define (c-string->symbol s)
 (string->symbol
  (list->string
   (map char-upcase
	(string->list
	 (pregexp-replace* "_" s "-"))))))

(define (with-file-stream f filename mode)
 (let* ((file (fopen filename mode))
	(result (f file)))
  (fclose file)
  result))

(define (with-buffer-stream f buffer size mode)
 (let* ((file (fmemopen buffer size mode))
	(result (f file)))
  (fclose file)
  result))

(define fclose (c-function void ("fclose" pointer)))
(define fopen (c-function pointer ("fopen" string string)))
(define fmemopen (c-function pointer ("fmemopen" pointer unsigned string)))

(define (with-c-string str f)
 (with-alloc (+ (string-length str) 1)
	     (lambda (buf)
	      (for-each-indexed
	       (lambda (c i) (c-byte-set! buf i (char->integer c)))
	       (string->list str))
              (c-byte-set! buf (string-length str) 0)
	      (f buf (+ (string-length str) 1)))))


(define (with-c-strings f strings)
 (let* ((c-strings (map-vector (lambda (str) (string->c-string str)) strings))
	(r (with-c-pointers f c-strings)))
  (for-each-vector (lambda (c-str) (free c-str)) c-strings)
  r))

(define (string->c-string str)
 (let ((c-str (malloc (+ (string-length str) 1))))
  ;;(bzero c-str (+ (string-length str) 1))
  (for-each-indexed
   (lambda (c i) (c-byte-set! c-str i (char->integer c)))
   (string->list str))
  (c-byte-set! c-str (string-length str) 0)
  c-str))

(define (c-null-separated-strings->strings c-strings)
 (let loop ((c-strings c-strings) (strings '()) (i 0))
  (if (= 0 (c-s2cuint-ref c-strings 0))
      (reverse strings)
      (loop (+ c-strings (c-sizeof "void*"))
	    (cons (c-string->string (c-s2cuint-ref c-strings 0))
		  strings)
	    (+ i 1)))))

(define (with-vector->c-array f set-element element-size v)
 (with-array (vector-length v) element-size
	     (lambda (array)
	      (f (vector->c-array array v set-element element-size)))))

(define (with-vector->c-exact-array f element-size v signed?)
 (with-array (vector-length v) element-size
	     (lambda (array)
	      (f (vector->c-exact-array array v element-size signed?)))))

(define (with-c-pointers f v)
 (with-vector->c-array f c-s2cuint-set! c-sizeof-s2cuint v))

(define (list->c-array array l set-element element-size)
 (for-each-indexed (lambda (x i) (set-element array (* i element-size) x)) l)
 array)

(define (list->c-inexact-array array l element-size signed?)
  (list->c-array
   array
   l
   (c-sized-inexact-ptr-set! element-size signed?)
   element-size))

(define (list->c-exact-array array l element-size signed?)
  (list->c-array
   array
   l
   (c-sized-int-ptr-set! element-size signed?)
   element-size))

(define (vector->c-array array v set-element element-size)
 (for-each-vector
  (lambda (x i) (set-element array (* i element-size) x))
  v
  (enumerate-vector (vector-length v)))
 array)

(define (vector->c-inexact-array array v element-size signed?)
 (vector->c-array
  array
  v
  (c-sized-inexact-ptr-set! element-size signed?)
  element-size))

(define (vector->c-exact-array array v element-size signed?)
 (vector->c-array
  array
  v
  (c-sized-int-ptr-set! element-size signed?)
  element-size))

(define (with-array elements element-size f)
  (with-alloc (* elements element-size) f))

(define (c-array->list array get-element element-size nr-elements)
  (vector->list (c-array->vector array get-element element-size nr-elements)))

(define (c-array->vector array get-element element-size nr-elements)
  (map-n-vector
   (lambda (x) (get-element array (* x element-size))) nr-elements))

(define (c-exact-array->list array element-size nr-elements signed?)
  (vector->list
   (c-exact-array->vector array element-size nr-elements signed?)))

(define (c-exact-array->vector array element-size nr-elements signed?)
  (c-array->vector
   array
   (c-sized-int-ptr-ref element-size signed?) element-size nr-elements))

(define (c-inexact-array->list array element-size nr-elements signed?)
  (vector->list
   (c-inexact-array->vector array element-size nr-elements signed?)))

(define (c-inexact-array->vector array element-size nr-elements signed?)
  (c-array->vector
   array
   (c-sized-inexact-ptr-ref element-size signed?) element-size nr-elements))

(define (c-ptr-byte-offset ptr off)
  ((lap (ptr off)
	(POINTER_TSCP (PLUS
		       ("(char*)" (TSCP_POINTER ptr))
		       (TSCP_S2CINT off))))
   ptr off))

(define (c-sized-int-ptr-ref size signed?)
  (cond
   ((= size 1) c-byte-ref)
   ((= size c-sizeof-short) (if signed? c-shortint-ref c-shortunsigned-ref))
   ((= size c-sizeof-int)   (if signed? c-int-ref c-unsigned-ref))
   ((= size c-sizeof-long)  (if signed? c-longint-ref c-longunsigned-ref))
   (else (fuck-up))))

(define (c-sized-int-ptr-set! size signed?)
  (cond
   ((= size 1) c-byte-set!)
   ((= size c-sizeof-short) (if signed? c-shortint-set! c-shortunsigned-set!))
   ((= size c-sizeof-int)   (if signed? c-int-set! c-unsigned-set!))
   ((= size c-sizeof-long)  (if signed? c-longint-set! c-longunsigned-set!))
   (else (fuck-up))))

(define (c-sized-inexact-ptr-ref size signed?)
  (cond
   ((= size c-sizeof-float) c-float-ref)
   ((= size c-sizeof-double) c-double-ref)
   (else (fuck-up))))

(define (c-sized-inexact-ptr-set! size signed?)
  (cond
   ((= size c-sizeof-float) c-float-set!)
   ((= size c-sizeof-double) c-double-set!)
   (else (fuck-up))))

(define (matrix->c-array array m set-element element-size)
 (for-each-n
   (lambda (i)
    (for-each-n
     (lambda (j)
      (c-float-set!
       array
       (* c-sizeof-float (+ j (* i (matrix-columns m))))
       (matrix-ref m i j)))
     (matrix-columns m)))
   (matrix-rows m)))

(define (pgm->float-buffer! pgm)
 (let ((array (malloc (* c-sizeof-float (pnm-width pgm) (pnm-height pgm)))))
  (matrix->c-array array (map-vector
			  (lambda (e)
			   (map-vector
			    (lambda (e) (* (/ e (pgm-maxval pgm)) 255))
			    e))
			  (pgm-grey pgm))
		   c-float-set!
		   c-sizeof-float)
  array))

(define popen (c-function pointer ("popen" string string)))
(define pclose (c-function int ("pclose" pointer)))

(define srand (c-function void ("srand" unsigned)))

(define setenv (c-function int ("setenv" string string bool)))
