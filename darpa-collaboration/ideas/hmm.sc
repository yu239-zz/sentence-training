;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;
(define *log-math-precision* 35.0)
(define (my-zero? x) (< (abs x) 1e-7))
;; x:length v:vector
(define-structure rvec x v)
;; x:columns y:rows m:matrix
(define-structure rmat x y m)                
(define-structure rmat-3d x y z m)
;; logA:rmat logB:rvec uu:int
(define-structure hmm logA logB uu)

(define *g-alpha* (make-rmat 0 0 '#(#())))
(define *g-delta* (make-rmat 0 0 '#(#())))
(define *g-deltav* (make-rmat 0 0 '#(#())))
(define *g-beta* (make-rmat 0 0 '#(#())))
(define *g-psi* (make-rmat-3d 0 0 0 '#(#(#()))))

(define (add-exp e1 e2)
 (let ((e-max (max e1 e2))
       (e-min (min e1 e2)))
  (cond ((= e-max minus-infinity) minus-infinity)
	((> (- e-max e-min) *log-math-precision*) e-max)
	(else (+ (my-log (+ 1.0 (my-exp (- e-min e-max)))) e-max)))))

(define (my-exp x) (exp x))
(define (my-log x) (log x))
(define (my-atan2 x y)
 (if (and (= x 0.0) (= y -1.0))
     (- pi)
     (atan x y)))

(define (add-rvec u v)
 (map-vector (lambda (iu iv) (+ iu iv)) u v))

(define (copy-rvec v)
 (map-vector identity v))

(define (dot-prod-rvec u v)
 (map-reduce-vector + 0 * u v))

(define (normalise-rvec v)
 (let ((max-iv (reduce-vector max (map-vector abs v) -1)))
  (map-vector
   (lambda (iv)
    (/ iv (if (= max-iv 0.0) 1.0 max-iv)))
   v)))

(define (scale-rvec v k)
 (map-vector (lambda (iv) (* k iv)) v))

(define (sum-of-logs v)
 (reduce-vector add-exp v minus-infinity))

(define (sum-rvec v)
 (reduce-vector + v 0))

(define (lbessi0 xx)
 (if (< (abs xx) 3.75)
     (let ((yy (sqr (/ xx 3.75))))
      (my-log
       (+ 1.0 (* yy (+ 3.5156229 (* yy (+ 3.0899424 (* yy (+ 1.2067492 (* yy (+ 0.2659732 (* yy (+ 0.360768e-1 (* yy 0.45813e-2))))))))))))))
     (let* ((yy (/ 3.75 (abs xx)))
	    (x1 (- (abs xx) (* 0.5 (my-log (abs xx)))))
	    (x2 (my-log (+ 0.39894228 (* yy (+ 0.1328592e-1 (* yy (+ 0.225319e-2 (* yy (+ -0.157565e-2 (* yy (+ 0.916281e-2 (* yy (+ -0.2057706e-1 (* yy (+ 0.2635537e-1 (* yy (+ -0.1647633e-1 (* yy 0.392377e-2)))))))))))))))))))
      (+ x1 x2))))

(define (lbessi1 xx)
 (unless (>= xx 0.0) (panic "lbessil1: x must be non-negative!"))
 (if (< (abs xx) 3.75)
     (let ((yy (sqr (/ xx 3.75))))
      (my-log
       (* (abs xx) (+ 0.5 (* yy (+ 0.87890594 (* yy (+ 0.51498869 (* yy (+ 0.15084934 (* yy (+ 0.2658733e-1 (* yy (+ 0.301532e-2 (* yy 0.32411e-3)))))))))))))))
     (let* ((yy (/ 3.75 (abs xx)))
	    (x1 (- (abs xx) (* 0.5 (my-log (abs xx)))))
	    (x2 (my-log (+ 0.39894228 (* yy (+ -0.3988024e-1 (* yy (+ -0.362018e-2 (* yy (+ 0.163801e-2 (* yy (+ -0.1031555e-1 (* yy (+ 0.2282967e-1 (* yy (+ -0.2895312e-1 (* yy (- 0.1787654e-1 (* yy 0.420059e-2)))))))))))))))))))
      (+ x1 x2))))

(define (constant-hmm uu upper-triangular?)
 (let* ((logB (make-rvec uu (make-vector uu 0)))
	(logA (make-rmat uu uu (map-n-vector
				(lambda (u)
				 (map-n-vector
				  (lambda (v)
				   (if (and upper-triangular? (> u v))
				       minus-infinity
				       0))
				  uu))
				uu)))
	(m (make-hmm logA logB uu)))
  (normalise-hmm m upper-triangular?)))

(define (copy-hmm m)
 (make-hmm
  (make-rmat (hmm-uu m) (hmm-uu m) (map-vector (lambda (v) (copy-rvec v)) (hmm-logA m)))
  (make-rvec (hmm-uu m) (copy-rvec (hmm-logB m)))
  (hmm-uu m)))

(define (normalise-hmm m upper-triangular?)
 (let ((delta-correction (my-log 1.0e-300))
       (logA (hmm-logA m))
       (logB (hmm-logB m))
       (uu (hmm-uu m))
       (sum (sum-of-logs (rvec-v logB))))
  (unless (> sum minus-infinity) (panic "normalise-hmm: sum is -inf."))
  (let ((new-v (map-vector (lambda (iv) (- iv sum)) (rvec-v logB)))
	(new-mat (map-vector
		  (lambda (v)
		   (let* ((sum (sum-of-logs v))
			  (v (if (= sum minus-infinity)
				 (map-vector-indexed
				  (lambda (iv i)
				   (if (and upper-triangular? (> u v))
				       iv
				       delta-correction))
				  v)
				 v))
			  (sum (sum-of-logs v)))
		    (map-vector (lambda (iv) (- v sum)) v)
		    ))
		  (rmat-m logA)))
	(make-hmm new-mat new-v uu)))))

;; f :: e -> (e, ?)
;; g :: (?, e) -> e
(define (map-with-flag-and-sum f g)
 '())

;; return (b-success new-m new-xu)
(define (normalise-hmm-linear m upper-triangular? train-mode xu)
 (call-with-current-continuation
  (lambda (return)
 (let* ((delta-correction 1e-20)
	(logA (hmm-logA m))
	(logB (hmm-logB m))
	(uu (hmm-uu m))
	(sum (reduce + (rvec-v logB) 0))
	(empty (empty (list #f '#(#()) '#()))))
  (unless (> (abs sum) 0.0) (panic "normalise-hmm-linear: sum is 0.0"))
  (let ((new-v (map-vector (lambda (iv) (/ iv sum)) (rvec-v logB))))
   (when (some negative? (vector->list new-v))
    (return (list #f '#(#()) '#())))
   (let* ((flag 0)
	  (new-v (map-vector
		  (lambda (iv)
		   (if (and (= train-mode 'hmm-dt)
			    (not (= (my-log iv) minus-infinity))
			    (< (my-log iv) (my-log delta-correction)))
		       (begin (format #t "logB: ~a is replaced by 0.0!~%" iv)
			      (set! flag 1)
			      minus-infinity)
		       (my-log iv)))
		  new-v))
	  (corrected-sum (sum-of-logs new-v))
	  (new-v (if (= flag 1)
		     (map-vector (lambda (iv) (- iv corrected-sum)) new-v)
		     new-v))
	  (new-rmat (map-vector
		     (lambda (v)
		      (let ((sum (reduce + v 0)))
		       (if (= sum 0.0)
			   (make-vector (vector-length v) minus-infinity)
			   (map-vector (lambda (iv) (/ iv sum)) v))))
		     (rmat-m logA)))
	  (_ (when (some-matrix negative? new-rmat) (return empty)))
	  (new-rmat (map-vector
		     (lambda (v)
		      (if (every (lambda (iv) (= v minus-infinity)) v)
			  v
			  (let* ((flag 0)
				 (new-v (map-vector
					 (lambda (iv)
					  (if (and (= train-mode 'hmm-dt)
						   (not (= (my-log iv) minus-infinity))
						   (< (my-log iv) (my-log delta-correction)))
					      (begin (format #t "logA: ~a is replaced by 0.0!~%" iv)
						     (set! flag 1)
						     minus-infinity)
					      (my-log iv)))
					 v))
				 (corrected-sum (sum-of-logs new-v))
				 (new-v (if (= flag 1)
					    (map-vector (lambda (iv) (- iv corrected-sum)) new-v)
					    new-v)))
			   new-v)))
		     new-rmat)))
    
	(let ()
	 '())))))))
	    
	
(define i matrix-ref)
	      
       
(some (lambda (v) (some negative? v))
		  (map (lambda (v) (vector->list v)) (vector->list new-rmat)))

(define (some-matrix p m) (some-vector (lambda (v) (some-vector p v)) m))



