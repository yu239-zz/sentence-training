(MODULE
  SENTENCE-TRAINING
  (WITH
    QOBISCHEME
    XLIB
    HMM-TRAIN-CLASSIFY
    HMM-WBM
    IDEALIB-HASH-TABLE
    IDEALIB-MATPLOTLIB
    IDEALIB-PREGEXP
    IDEALIB-TRACKS
    IDEALIB-STUFF
    TOOLLIB-C-BINDINGS
    TOOLLIB-CAMERA
    TOOLLIB-HACK-TRACK-DRAWABLE
    TOOLLIB-HACK-TRACK
    TOOLLIB-IMAGE-PROCESSING
    TOOLLIB-MATLAB
    TOOLLIB-MISC)
  (MAIN MAIN))

(include "QobiScheme-AD.sch")
(include "sentence-training.sch")
(include "toollib-c-macros.sch")

(set! *program* "sentence-training")
(set! *panic?* #f)

;;;;;
(define *model-threshold-tracker-offset* -0.4)
(define *profile-best-boxes?* #f)
(define *model-groups* '())
(define *negatives-f* #f)
(define *initializing-f* #f)
(define *training-multiple-f* #f)
(define *cfg* #f)
(define *semantics* #f)
(define *training-mode* #f)
(define *objects* '())
(define *feature-medoids* '())
(define *video-directory* #f)
(define *parameters* '())
(define *model-constraint* #f)
(define *models* #f)
(define *sentence-annotation* #f)
(define *box-scores-table* #f)
(define *track-scores-table* #f)
(define *features-tables* #f)
(define *init-paras* '#())
(define *last-paras* '#())
(define *c-feature-medoids* #f)
(define *phase* 0)
(define *stored-negatives-multiple* #f)
(define *stored-phase* #f)
(define *stored-n-iteration* 0)
;; Cached computed results in the previous iteration for DT
(define *cached-derivatives* '#())
(define *cached-objective-value* #f)

;; Global option
(define-structure global-option
 prediction-lookahead
 model-path
 result-path
 top-n
 n-phases
 restart-times
 noise-delta
 tolerance
 dt-mode
 d-delta
 d-step
 maximum-d
 initialized-d
 maximum-iteration
 negatives-n
 upper-triangular?
 verbose?)

(define (global-option->pretty-string options)
 (apply
  string-append
  (list
   (format #f "Prediction-lookahead:           ~a~%" (global-option-prediction-lookahead options))
   (format #f "Model-path:                     ~a~%" (global-option-model-path options))
   (format #f "Result-path:                    ~a~%" (global-option-result-path options))
   (format #f "Top-n:                          ~a~%" (global-option-top-n options))
   (format #f "N-phases:                       ~a~%" (global-option-n-phases options))
   (format #f "Restart-times:                  ~a~%" (global-option-restart-times options))
   (format #f "Noise-delta:                    ~a~%" (global-option-noise-delta options))
   (format #f "Tolerance:                      ~a~%" (global-option-tolerance options))
   (format #f "DT-mode:                        ~a~%" (global-option-dt-mode options))
   (format #f "D-delta:                        ~a~%" (global-option-d-delta options))
   (format #f "D-step:                         ~a~%" (global-option-d-step options))
   (format #f "Maximum-d:                      ~a~%" (global-option-maximum-d options))
   (format #f "Initialized-d:                  ~a~%" (global-option-initialized-d options))
   (format #f "Maximum-iteration:              ~a~%" (global-option-maximum-iteration options))
   (format #f "Negatives-n:                    ~a~%" (global-option-negatives-n options))
   (format #f "Upper-triangular:               ~a~%" (global-option-upper-triangular? options))
   (format #f "Verbose:                        ~a~%" (global-option-verbose? options)))))

(define (prediction-lookahead) (global-option-prediction-lookahead *parameters*))
(define (model-path) (global-option-model-path *parameters*))
(define (result-path) (global-option-result-path *parameters*))
(define (top-n) (global-option-top-n *parameters*))
(define (n-phases) (global-option-n-phases *parameters*))
(define (restart-times) (global-option-restart-times *parameters*))
(define (noise-delta) (global-option-noise-delta *parameters*))
(define (tolerance) (global-option-tolerance *parameters*))
(define (dt-mode) (global-option-dt-mode *parameters*))
(define (d-delta) (global-option-d-delta *parameters*))
(define (d-step) (global-option-d-step *parameters*))
(define (maximum-d) (global-option-maximum-d *parameters*))
(define (initialized-d) (global-option-initialized-d *parameters*))
(define (maximum-iteration) (global-option-maximum-iteration *parameters*))
(define (negatives-n) (global-option-negatives-n *parameters*))
(define (upper-triangular?) (global-option-upper-triangular? *parameters*))
(define (verbose?) (global-option-verbose? *parameters*))

(define (tailed-sublist list start end)
 (if (zero? start)
     (let loop ((list list) (k end) (res '()))
      (if (zero? k)
	  (reverse res)
	  (loop (cdr list) (- k 1) (cons (car list) res))))
     (tailed-sublist (cdr list) (- start 1) (- end 1))))

(define (divide-list-into-n-parts l n)
 (let* ((len (length l))
	(part-length (round (/ len n))))
  (let loop ((l l)
	     (i 1))
   (if (= i n)
       (list l)
       (cons (tailed-sublist l 0 part-length)
	     (loop (tailed-sublist l part-length (length l))
		   (+ i 1)))))))

(define (get-scale video) (/ (first (video-info video)) 1280))

(define (article? word)
 (or (equal? (string-downcase word) "an")
     (equal? (string-downcase word) "the")))

(define (rand-select lst n)
 (map second
      (take-if-possible n (sort (map (lambda (e) (list (rand) e)) lst) < first))))

(define (inference-video? v)
 (member v '("MVI_0824.mov" "MVI_0827.mov" "MVI_0830.mov" "MVI_0834.mov"
	     "MVI_0837.mov" "MVI_0840.mov" "MVI_0844.mov" "MVI_0847.mov"
	     "MVI_0856.mov" "MVI_0859.mov" "MVI_0862.mov" "MVI_0867.mov"
	     "MVI_0870.mov" "MVI_0874.mov" "MVI_0881.mov" "MVI_0884.mov"
	     "MVI_0888.mov" "MVI_0891.mov" "MVI_0894.mov" "MVI_0897.mov"
	     "MVI_0900.mov" "MVI_0903.mov" "MVI_0907.mov" "MVI_0910.mov" "MVI_0915.mov")))

(define (pos-generalize pos)
 (case pos
  ((dtv tv) 'v)
  ((pl) 'p)
  (else pos)))

(define (training-mode-ml? training-mode)
 (equal? training-mode training-mode-c:ml))

(define (generate-negatives negatives-f sentence video-name cfg semantics train-mode)
 (if (training-mode-ml? train-mode)
     '()
     (negatives-f sentence cfg semantics video-name)))

(define (negatives-replace-words sentence video-name cfg semantics parts-of-speech)
 (define (sub-pos? p1 p2)
  (equal? (pos-generalize p1) p2))
 (let* ((theta-roles (theta-role-assignments sentence cfg semantics))
	(targets
	 (map (lambda (part-of-speech)
	       (list part-of-speech
		     (map (lambda (rule)
			   (string-join " " (map (o string-downcase symbol->string) (z rule))))
			  (remove-if (lambda (rule)
				      (or (not (sub-pos? (y rule) part-of-speech))
					  (member 'object (z rule))))
				     (cfg-rules cfg)))))
	      parts-of-speech))
	(clean?
	 (lambda (s)
	  (let* ((theta-roles1 (remove-if (lambda (tr) (member (first tr) '(d n))) theta-roles))
		 (theta-roles2 (remove-if (lambda (tr) (member (first tr) '(d n)))
					  (theta-role-assignments s cfg semantics))))
	   (some (lambda (r1 r2)
		  (case (first r1)
		   ((v) (let ((agent1 (x (third r1)))
			      (patient1 (x (fourth r1)))
			      (agent2 (x (third r2)))
			      (patient2 (x (fourth r2)))
			      (verb1 (second r1))
			      (verb2 (second r2)))
			 (case verb1
			  ((approached)
			   (case verb2
			    ((approached) (or (not (equal? patient1 patient2))
					      (equal? patient1 agent2)))
			    ((carried) (or (member patient1 (list patient2 agent2))
					   (equal? patient2 'person)
					   (not (equal? agent2 'person))))
			    (else #t)))
			  ((carried)
			   (case verb2
			    ((approached) (or (member patient2 (list agent1 patient1))
					      (not (member agent2 (list agent1 patient1)))))
			    ((carried) (or (not (equal? agent1 agent2))
					   (not (equal? patient1 patient2))))
			    (else #t)))
			  ((picked-up)
			   (case verb2
			    ((picked-up) (or (not (equal? agent1 agent2))
					     (not (equal? patient1 patient2))))
			    (else #t)))
			  ((put-down)
			   (case verb2
			    ((put-down) (or (not (equal? agent1 agent2))
					    (not (equal? patient1 patient2))))
			    (else #t))))))
		   ((p) (let ((agent1 (x (third r1)))
			      (referent1 (x (fourth r1)))
			      (agent2 (x (third r2)))
			      (referent2 (x (fourth r2)))
			      (p1 (second r1))
			      (p2 (second r2)))
			 (or (and (not (equal? p1 p2))
				  (equal? agent1 agent2)
				  (equal? referent1 referent2))
			     (equal? agent2 referent2))))
		   ((pm) (let ((agent1 (x (third r1)))
			       (goal1 (x (fourth r1)))
			       (agent2 (x (third r2)))
			       (goal2 (x (fourth r2)))
			       (pm1 (second r1))
			       (pm2 (second r2)))
			  (or (and (not (equal? pm1 pm2))
				   (equal? agent1 agent2)
				   (equal? goal1 goal2))
			      (equal? agent2 goal2))))
		   ((adv) (let ((agent1 (x (third r1)))
				(adv1 (second r1))
				(agent2 (x (third r2)))
				(adv2 (second r2)))
			   (if (equal? adv1 adv2)
			       (not (equal? agent1 agent2))
			       (equal? agent1 agent2))))))
		 theta-roles1 theta-roles2))))
	(off-the-list?
	 (lambda (s)
	  (not (member
		s
		(map string-downcase
		     (second (assoc video-name
				    *sentence-annotation*))))))))
  (when (and (equal? cfg *new4-corpus:cfg*)
  	     (not (member (string-downcase sentence)
  			  (map string-downcase
  			       (second (assoc video-name
  					      *sentence-annotation*))))))
   (panic "negatives-replace-words: sentence should be on the list"))
  
  (rand-select
   (remove-if-not
    (cond ((equal? cfg *new3-corpus:cfg*) clean?)
	  ((equal? cfg *new4-corpus:cfg*) off-the-list?)
	  (else (fuck-up)))
    (if (equal? cfg *new3-corpus:cfg*)
	(all-values
	 (string-join
	  " "
	  (map
	   (lambda (role) 
	    (let* ((word (string-downcase (symbol->string (second role))))
		   (word (if (equal? (first role) 'n)
			     word
			     (pregexp-replace* "-" word " ")))
		   (candidates
		    (if (member (first role) parts-of-speech)
			(second (assoc (first role) targets))
			(list word))))
	     (a-member-of candidates)))
	   theta-roles)))
	(let ((noun-candidates (second (assoc 'n targets)))
	      (verb-candidates (remove "replaced" (remove "gave" (second (assoc 'v targets))))))
	 (append
	  (all-values
	   (string-join " " (list "the" (a-member-of noun-candidates)
				  (a-member-of verb-candidates) "the"
				  (a-member-of noun-candidates))))
	  (cond ((equal? cfg *new4-corpus:cfg*)
	  	 (append
	  	  (all-values
	  	   (string-join " " (list "the" (a-member-of noun-candidates)
	  				  "gave the" (a-member-of noun-candidates)
	  				  "to the" (a-member-of noun-candidates))))
	  	  (all-values
	  	   (string-join " " (list "the" (a-member-of noun-candidates)
	  				  "replaced the" (a-member-of noun-candidates)
	  				  "with the" (a-member-of noun-candidates))))))
	  	((equal? cfg *new3-corpus:cfg*) '())
	  	(else (fuck-up)))
	  ))))
   (negatives-n))))

;; This only packs the parameters
;; Other model information will lose
(define (pack models)
 (let loop ((models models)
	    (paras '())
	    (statistics '()))
  (if (null? models)
      (list (list->vector paras) statistics)
      (let* ((current-model (first models))
	     (uu (model-uu current-model))
	     (ii (model-ii current-model))
	     (nns (map-n (lambda (i) (model-nn current-model i)) ii)))
       (loop
	(rest models)
	(append
	 paras
	 (append
	  ;; b vector
	  (map-n (lambda (u) (model-b current-model u)) uu)
	  ;; a matrix
	  (map-n (lambda (u) (model-a current-model (quotient u uu) (modulo u uu)))
		 (* uu uu))
	  ;; distributions
	  (join (map-n (lambda (u)
			(join
			 (map-n (lambda (i)
				 (map-n (lambda (n)
					 (model-parameter current-model u i (+ n 1)))
					(list-ref nns i))) ii))) uu))))
	(append statistics (list (list uu ii nns))))))))

(define (unpack paras statistics model-names model-pos model-n-roles)
 (map (lambda (stat name pos n-roles)
       (let* ((uu (first stat))
	      (ii (second stat))
	      (nns (third stat))
	      (psi-model
	       (make-psi
		(c-string->symbol name)
		pos #f n-roles
		(let ((parameters (make-matrix uu ii)))
		 (do ((u 0 (+ u 1))) ((= u uu))
		  (do ((i 0 (+ i 1))) ((= i ii))
		   (matrix-set!
		    parameters
		    u
		    i
		    (cons
		     'discrete
		     (map-n
		      (lambda (n)
		       (vector-ref paras
				   (+ uu (* uu uu)
				      (* u (reduce + nns 0))
				      (reduce + (take-if-possible i nns) 0) n)))
		      (list-ref nns i))))))
		 parameters)
		(let ((a (make-matrix uu uu)))
		 (do ((u 0 (+ u 1))) ((= u uu))
		  (do ((v 0 (+ v 1))) ((= v uu))
		   (matrix-set! a u v (vector-ref paras (+ uu (* u uu) v)))))
		 a)
		(let ((b (make-vector uu)))
		 (do ((u 0 (+ u 1))) ((= u uu))
		  (vector-set! b u (vector-ref paras u)))
		 b))))
	(set! paras (list->vector (drop (+ uu (* uu uu) (* uu (reduce + nns 0)))
					(vector->list paras))))
	psi-model))
      statistics model-names model-pos model-n-roles))

(define (compute-sentence-prior sentence)
 (map-reduce
  + 0
  (lambda (psi)
   (+ (log (psi-uu psi))
      (reduce +
	      (map-n (lambda (i)
		      (log (length (rest (matrix-ref (psi-parameters psi) 0 i)))))
		     (psi-ii psi))
	      0)))
  sentence))

(define (a-set-of-indices indices-multiple)
 (nondeterministic-map a-member-of indices-multiple))

(define (check-model-constraint boxes model-constraint machine-roles)
 (unless (equal? model-constraint model-constraint:none)
  (for-each (lambda (roles)
	     (when (and (= (length roles) 2)
			(zero? (box-similarity
				(list-ref boxes (first roles))
				(list-ref boxes (second roles)))))
	      (fail))) machine-roles)))

(define (check-boxes-consistence prev-boxes boxes)
 (unless (= (length prev-boxes) (length boxes))
  (panic "check-boxes-consistence: unequal length"))
 (map-reduce min 0 (lambda (prev-box box)
		    (box-similarity prev-box box))
	     prev-boxes boxes))

(define (eliminate-minus-infinities alphas)
 (remove-if (lambda (alpha) (equal? minus-infinity (first alpha))) alphas))

(define (single-and-pair-box-scores boxes-movie flow-movie scale)
 (list
  (map (lambda (boxes)
	(list->vector (map box-cost boxes)))
       boxes-movie)
  (map (lambda (boxes flow next-boxes)
	(list-of-lists->matrix
	 (map (lambda (box)
	       (map (lambda (next-box)
		     (box-pair-cost box flow next-box scale))
		    next-boxes))
	      boxes)))
       (but-last boxes-movie)
       (but-last flow-movie)
       (drop 1 boxes-movie))))

(define (decode-x-psi-states psis u)
 (let loop ((psis (reverse psis))
	    (u u)
	    (us '()))
  (if (null? psis)
      us
      (let ((w-uu (psi-uu (first psis))))
       (loop (rest psis) (quotient u w-uu) (cons (modulo u w-uu) us))))))

(define (decode-box-states j ii n-tracks)
 (let loop ((n-tracks n-tracks)
	    (j j)
	    (js '()))
  (if (zero? n-tracks)
      (reverse js)
      (loop (- n-tracks 1) (quotient j ii) (cons (modulo j ii) js)))))

(define (x-box-state js ii)
 (let loop ((js (reverse js))
	    (j 0))
  (if (null? js)
      j
      (loop (rest js) (+ (* j ii) (first js))))))

(define (x-psi-state-range psis)
 (map-reduce * 1 (o vector-length psi-b) psis))

(define (x-log-b psis u)
 (initial-states-score (decode-x-psi-states psis u) psis))

(define (x-log-a psis u v)
 (states-transition-score
  (decode-x-psi-states psis u) (decode-x-psi-states psis v) psis))

(define (make-xpsi sentence)
 (let ((uu (x-psi-state-range sentence)))
  (make-psi 'tba 0 #f 0
	    '#()
	    (let ((a (make-matrix uu uu)))
	     (do ((u 0 (+ u 1))) ((= u uu))
	      (do ((v 0 (+ v 1))) ((= v uu))
	       (matrix-set! a u v (x-log-a sentence u v))))
	     a)
	    (let ((b (make-vector uu)))
	     (do ((u 0 (+ u 1))) ((= u uu))
	      (vector-set! b u (x-log-b sentence u)))
	     b))))

(define (select-out-boxes boxes indices)
 (map (lambda (idx) (list-ref boxes idx)) indices))

(define (compute-eligible-states n-tracks machine-roles boxes-movie model-constraint)
 (unless (every (lambda (boxes)
		 (every (lambda (box1 box2)
			 (zero? (box-similarity box1 box2)))
			boxes (first boxes-movie)))
		boxes-movie)
  (panic "compute-eligible-states: order of box models in every frame is not consistent"))

 (let* ((first-boxes (first boxes-movie))
	(tracks-indices
	 (map-n (lambda _ (enumerate (length first-boxes))) n-tracks))
	(eligible-box-states
	 (all-values
	  (let* ((indices (a-set-of-indices tracks-indices))
		 (boxes (select-out-boxes first-boxes indices)))
	   (check-model-constraint boxes model-constraint machine-roles)
	   indices)))
	(secondary-eligible-box-states
	 (map (lambda (es)
	       (map first
		    (remove-if
		     (lambda (es2)
		      (= minus-infinity (second es2)))
		     (map-indexed
		      (lambda (es2 j)
		       (list j (check-boxes-consistence (select-out-boxes first-boxes es2)
							(select-out-boxes first-boxes es))))
		      eligible-box-states))))
	      eligible-box-states)))
  (list (list->vector eligible-box-states)
	(list->vector secondary-eligible-box-states))))

(define (viterbi-tracker-score machine-roles eligible-states secondary-eligible-states
			       single-box-scores pair-box-scores)
 (let* (;; t = 0
	(alphas	(map-vector
		 (lambda (es)
		  (boxes-single-score (first single-box-scores) es))
		 eligible-states)))
  ;; t > 0
  (let loop ((frame 1)
	     (alphas alphas)
	     (single-box-scores (rest single-box-scores))
	     ;; actually from t - 1 to t
	     (pair-box-scores pair-box-scores))
   (if (null? single-box-scores)
       ;; Add alpha at time tt - 1 to get the overall score
       (reduce my-add-exp1 (vector->list alphas) minus-infinity)
       (loop
	(+ frame 1)
	(map-vector
	 (lambda (es es2-for-es)
	  (+ (map-reduce my-add-exp1 minus-infinity
			 (lambda (Jk)
			  (let* ((es2 (vector-ref eligible-states Jk))
				 (pair (boxes-pair-score (first pair-box-scores) es2 es)))
			   (+ pair (vector-ref alphas Jk))))
			 es2-for-es)
	     (boxes-single-score (first single-box-scores) es)))
	 eligible-states secondary-eligible-states)
	(rest single-box-scores)
	(rest pair-box-scores))))))

(define (initial-states-score states sentence)
 (map-reduce + 0 (lambda (u psi)
		  (vector-ref (psi-b psi) u))
	     states sentence))

(define (states-transition-score states1 states2 sentence)
 (map-reduce + 0 (lambda (u v psi)
		  (matrix-ref (psi-a psi) u v))
	     states1 states2 sentence))

(define (new-features-sentence boxes-movie-id frame-id sentence boxes indices
			       machine-roles flow scale)
 (map
  (lambda (psi roles)
   (unless (= (length roles) (psi-n-roles psi))
    (panic "states-output-score"))
   (memoized-new-feature boxes-movie-id frame-id (psi-part-of-speech psi)
   			 boxes indices roles flow scale))
  sentence machine-roles))

(define (states-output-score states sentence new-features)
 (map-reduce
  + 0
  (lambda (u psi feature)
   (let ((parameters (vector-ref (psi-parameters psi) u)))
    (unless (= (vector-length feature)
	       (vector-length parameters))
     (panic "states-output-score: error"))
    (reduce + (vector->list
	       (map-vector (lambda (f dist)
			    (log (list-ref (rest dist) f)))
			   feature parameters)) 0)))
  states sentence new-features))

(define (boxes-single-score single-box-scores indices)
 (map-reduce + 0 (lambda (idx)
		  (vector-ref single-box-scores idx)) indices))

(define (boxes-pair-score pair-box-scores indices1 indices2)
 (reduce + (map (lambda (i j) (matrix-ref pair-box-scores i j))
		indices1 indices2) 0))

(define (compute-x-states-score boxes-movie-id eligible-states sentence machine-roles boxes-movie
				flow-movie scale)
 (let* ((vv (x-psi-state-range sentence))
	(jj (vector-length eligible-states)))
  (map-n
   (lambda (i)
    (let ((boxes-per-frame (list-ref boxes-movie i))
	  (flow (list-ref flow-movie i))
	  (out-l (make-vector (* jj vv) 0)))
     (do ((j 0 (+ j 1))) ((= j jj))
      (let* ((indices (vector-ref eligible-states j))
	     (boxes (select-out-boxes boxes-per-frame indices))
	     (features (new-features-sentence boxes-movie-id i sentence boxes indices
					      machine-roles flow scale)))
       (do ((u 0 (+ u 1))) ((= u vv))
	(let ((states (decode-x-psi-states sentence u)))
	 (vector-set!
	  out-l
	  (+ (* u jj) j)
	  (states-output-score states sentence features))))))
     out-l))      
   (length boxes-movie))))

;; sentence -> log psi models
(define (sentence-likelihood-and-prior boxes-movie-id sentence machine-roles boxes-movie flow-movie
				       track-score eligible-states secondary-eligible-states
				       single-box-scores pair-box-scores scale)
 (let* ((xpsi (make-xpsi sentence))
	(vv (psi-uu xpsi))
	(jj (vector-length eligible-states))
	(x-states-out-ls (compute-x-states-score
			  boxes-movie-id eligible-states sentence machine-roles
			  boxes-movie flow-movie scale))
	;; t = 0
	(alphas
	 (let ((alphas (make-vector (* jj vv) 0)))
	  (for-each-indexed-vector
	   (lambda (es j)
	    (let ((single (boxes-single-score (first single-box-scores) es)))
	     (do ((u 0 (+ u 1))) ((= u vv))
	      (vector-set!
	       alphas
	       (+ (* u jj) j)
	       (+ (vector-ref (psi-b xpsi) u)
		  (vector-ref (first x-states-out-ls) (+ (* u jj) j))
		  single)))))
	   eligible-states)
	  alphas)))
  ;; t > 0
  (let loop ((frame 1)
  	     (alphas alphas)
  	     (x-states-out-ls (rest x-states-out-ls))
  	     (single-box-scores (rest single-box-scores))
  	     ;; actually from t - 1 to t
  	     (pair-box-scores pair-box-scores))
   (if (null? x-states-out-ls)
       ;; Calculate likelihood per-frame
       (+ (/ (+ (reduce my-add-exp1 (vector->list alphas) minus-infinity)
  		(- track-score))
  	     frame)
  	  (compute-sentence-prior sentence))
       (loop
  	(+ frame 1)
  	(let ((new-alphas (make-vector (* jj vv) 0)))
  	 (for-each-indexed-vector
  	  (lambda (es-and-es2-for-es j)   ;; j from 0 to jj
  	   (let* ((es (x es-and-es2-for-es))
		  (es2-for-es (y es-and-es2-for-es))
		  (single (boxes-single-score (first single-box-scores) es)))
  	    (do ((u 0 (+ u 1))) ((= u vv))  ;; u from 0 to vv
  	     (let* ((ux (+ (* u jj) j))
		    (transition
		     (reduce my-add-exp1
			     (join (map (lambda (Jk) ;; k from 0 to kk
					 (let* ((es2 (vector-ref eligible-states Jk))
						(pair (boxes-pair-score (first pair-box-scores) es2 es)))
					  (map-n (lambda (v) ;; v from 0 to vv
						  (+ (vector-ref alphas (+ (* v jj) Jk))
						     (matrix-ref (psi-a xpsi) v u)
						     pair))
						 vv)))
					es2-for-es))
			     minus-infinity)))
  	      (vector-set! new-alphas ux
  			   (+ single transition (vector-ref (first x-states-out-ls) ux)))))
	    ))
  	  (transpose (vector eligible-states secondary-eligible-states)))
  	 new-alphas)
	(rest x-states-out-ls)
  	(rest single-box-scores)
  	(rest pair-box-scores))))))

(define (sentence-derivatives paras sentence machine-roles boxes-movie-id boxes-movie flow-movie
			      n-tracks single-box-scores pair-box-scores scale
			      statistics model-names model-pos model-n-roles)
 (define (derivative-f f v0) ((gradient-R f) v0))
 (let* ((eligible-states (compute-eligible-states
			  n-tracks machine-roles boxes-movie *model-constraint*))
	(track-score
	 (memoized-track-score boxes-movie-id machine-roles eligible-states
			       single-box-scores pair-box-scores))
	(block-size (map (lambda (info)
			  (let ((uu (first info)))
			   (+ (* uu (+ uu 1 (reduce + (third info) 0)))))) statistics))
	(real-paras-and-real->unreal
	 (let loop ((idx 0)
		    (block-size block-size)
		    (paras (vector->list paras))
		    (real-paras '())
		    (real->unreal '()))
	  (if (null? block-size)
	      (list (list->vector real-paras)
		    (reverse real->unreal))
	      (if (member idx sentence)
		  (loop (+ idx 1) (rest block-size) (drop (first block-size) paras)
			(append real-paras (take (first block-size) paras)) (cons idx real->unreal))
		  (loop (+ idx 1) (rest block-size) (drop (first block-size) paras)
			real-paras real->unreal)))))
	(real-paras (first real-paras-and-real->unreal))
	(real->unreal (second real-paras-and-real->unreal))
	(real-model-names (map (lambda (idx) (list-ref model-names idx)) real->unreal))
	(real-model-pos (map (lambda (idx) (list-ref model-pos idx)) real->unreal))
	(real-model-n-roles (map (lambda (idx) (list-ref model-n-roles idx)) real->unreal))
	(real-statistics (map (lambda (idx) (list-ref statistics idx)) real->unreal))
	(real-sentence (map (lambda (w) (position w real->unreal)) sentence))
	
	(flat-input-sentence-likelihood-linear
	 (lambda (x)
	  (let* ((psis (unpack x real-statistics real-model-names real-model-pos real-model-n-roles))
		 (_ (for-each (lambda (psi)
			       (begin
				(set-psi-a! psi (map-matrix log (psi-a psi)))
				(set-psi-b! psi (map-vector log (psi-b psi)))))
			      psis))
		 (sentence (map (lambda (idx) (list-ref psis idx)) real-sentence)))
	   (exp (sentence-likelihood-and-prior
		 boxes-movie-id sentence machine-roles boxes-movie flow-movie track-score
		 (first eligible-states) (second eligible-states)
		 single-box-scores pair-box-scores scale)))))
	(real-gradients (derivative-f flat-input-sentence-likelihood-linear real-paras)))
  
  (let loop ((idx 0)
             (block-size block-size)
	     (real-gradients (vector->list real-gradients))
	     (gradients '()))
   (if (null? block-size)
       (begin
	(unless (null? real-gradients) (panic "sentence-derivatives"))
	(list->vector gradients))
       (if (member idx real->unreal)
	   (loop (+ idx 1) (rest block-size) (drop (first block-size) real-gradients)
		 (append gradients (take (first block-size) real-gradients)))
	   (loop (+ idx 1) (rest block-size) real-gradients
		 (append gradients (vector->list (make-vector (first block-size) 0)))))))))

(define (sentence-derivatives-multiple
	 paras sentences roles-multiple boxes-movie flow-movie
	 n-tracks-multiple scale statistics model-names model-pos model-n-roles)
 (let* ((pack-info (allocate-pack-info (length statistics)))
	(c-roles-multiple (map (lambda (roles)
				(map (lambda (r)
				      (let ((r-vec (allocate-ivec (length r))))
				       (for-each-indexed (lambda (x i) (set-ivec! r-vec i x)) r)
				       r-vec))
				     roles))
			       roles-multiple))
	(wws (map length sentences))
	(c-flow-struct-movie (map (lambda (fl) (optical-flow->c-flow-struct fl)) flow-movie))
	(c-boxes-movie (map (lambda (boxes-per-frame)
			     (map voc4-detection->box boxes-per-frame))
			    boxes-movie))
	(c-boxes-movie-struct (allocate-boxes-movie (length c-boxes-movie)
						    (length (first c-boxes-movie)))))
  (for-each-indexed
   (lambda (boxes-per-frame t)
    (begin
     (unless (= (length boxes-per-frame) (* (length *model-groups*) (top-n)))
      (panic "derivatives<-sentence-multiple-or-objective: box number error ~a"
	     (length boxes-per-frame)))
     (for-each-indexed
      (lambda (box i)
       (set-boxes-movie! c-boxes-movie-struct t i box))
      boxes-per-frame)))
   c-boxes-movie)
  
  (for-each-indexed
   (lambda (info n)
    (let ((uu (first info))
	  (ii (second info))
	  (nns (third info)))
     (set-pack-info-uu! pack-info n uu)
     (set-pack-info-ii! pack-info n ii)
     (for-each-indexed (lambda (nn i) (set-pack-info-nn! pack-info n i nn)) nns)))
   statistics)
  
  (let* ((c-gradients
	  (with-vector->c-array
	   (lambda (paras-array)
	    (with-vector->c-exact-array
	     (lambda (sentence-array)
	      (with-vector->c-exact-array
	       (lambda (wws-array)
		(with-c-pointers
		 (lambda (c-roles-array)
		  (with-c-pointers
		   (lambda (c-flow-struct-array)
		    (with-vector->c-exact-array
		     (lambda (n-tracks-array)
		      (with-c-strings
		       (lambda (model-names-array)
			(with-vector->c-exact-array
			 (lambda (model-pos-array)
			  (with-vector->c-exact-array
			   (lambda (model-n-roles-array)
			    (with-c-strings
			     (lambda (objects-array)
			      (with-c-pointers
			       (lambda (c-feature-medoids-array)
				(sentence-derivatives-one-video    
				 paras-array (length sentences) (vector-length paras)
				 sentence-array wws-array n-tracks-array c-roles-array
				 c-boxes-movie-struct c-flow-struct-array (length flow-movie)
				 scale pack-info model-names-array model-pos-array
				 model-n-roles-array objects-array (length *objects*)
				 c-feature-medoids-array (length *c-feature-medoids*) *model-constraint*))
			       (list->vector *c-feature-medoids*)))
			     (list->vector *objects*)))
			   c-sizeof-int
			   (list->vector model-n-roles)
			   #t))
			 c-sizeof-int
			 (list->vector model-pos)
			 #t))
		       (list->vector model-names)))
		     c-sizeof-int
		     (list->vector n-tracks-multiple)
		     #t))
		   (list->vector c-flow-struct-movie)))
		 (list->vector (join c-roles-multiple))))
	       c-sizeof-int
	       (list->vector wws)
	       #t))
	     c-sizeof-int
	     (list->vector (join sentences))
	     #t))
	   c-double-set!
	   c-sizeof-double
	   paras))
	 (derivatives-multiple
	  (divide-list-into-n-parts
	   (c-inexact-array->list c-gradients c-sizeof-double
				  (* (vector-length paras) (length sentences)) #t)
	   (length sentences))))
   (free-pack-info pack-info)
   (for-each free-ivec (join c-roles-multiple))
   (for-each free-c-flow-struct c-flow-struct-movie)
   (for-each (lambda (boxes) (for-each free-box boxes)) c-boxes-movie)
   (free-boxes-movie c-boxes-movie-struct)
   (free c-gradients)
   (map list->vector derivatives-multiple))))

(define (memoized-box-scores boxes-movie-id boxes-movie flow-movie scale)
 (ref/compute *box-scores-table*
 	      boxes-movie-id
 	      (lambda (bm fm scale)
 	       (single-and-pair-box-scores bm fm scale))
 	      boxes-movie flow-movie scale))

(define (memoized-track-score boxes-movie-id machine-roles eligible-states
			      single-box-scores pair-box-scores)
 (ref/compute *track-scores-table*
 	      `(,boxes-movie-id ,machine-roles)
 	      (lambda (roles es single pair)
 	       (viterbi-tracker-score
 		roles (first es) (second es) single pair))
 	      machine-roles eligible-states
 	      single-box-scores pair-box-scores))

(define (memoized-new-feature boxes-movie-id frame-id part-of-speech boxes
			      indices roles flow scale)
 (when (> (length roles) 3)
  (panic "memoized-new-feature: Do not support more than three tracks"))
 (ref/compute (list-ref *features-tables* boxes-movie-id)
 	      `(,frame-id ,part-of-speech
 	      		  ,(map (lambda (r) (list-ref indices r)) roles))
 	      (lambda (pos box1 box2 box3 fl scale)
 	       (new-feature pos 2 box1 box2 box3 *objects* fl scale *c-feature-medoids*))
 	      part-of-speech
 	      (list-ref boxes (first roles))
 	      (if (< (length roles) 2) #f (list-ref boxes (second roles)))
	      (if (< (length roles) 3) #f (list-ref boxes (third roles)))
 	      flow scale))

(define (gt-estimation-one video boxes-movie-id likelihoods flow-movie boxes-movie sentence negatives
			   current-models paras statistics model-names model-pos model-n-roles ad?)
 (let* ((competition-set (cons sentence negatives))
	;; Each parsing result should be in the format:
	;; (hmms
	;;  role-participant-pair
	;;  roles-for-machines
	;;  theta-roles)
	(parsing-results
	 (map (lambda (s)
	       (sentence:sentence->participants-roles-and-state-machines
		s *cfg* *semantics* current-models))
	      competition-set))
	(machine-roles-multiple (map third parsing-results))
	(n-tracks-multiple (map (lambda (pr) (+ (maximum (reduce append (third pr) '())) 1))
				parsing-results))
	;; Get the psi indices
	(sentences (map (lambda (pr)
			 (map (lambda (m) (position m current-models))
			      (first pr)))
			parsing-results))
	(video-scale (get-scale video))
	(box-scores (memoized-box-scores boxes-movie-id boxes-movie flow-movie video-scale))
	(derivatives-multiple
	 (if ad?
	     (cond ((equal? (dt-mode) "gt-in-c")
		    (sentence-derivatives-multiple
		     ;;;; This smooth is for AD to avoid NaN
		     (map-vector smooth-zero paras) sentences machine-roles-multiple
		     boxes-movie flow-movie n-tracks-multiple video-scale statistics
		     model-names model-pos model-n-roles))
		   ((equal? (dt-mode) "gt-in-sc")
		    (map (lambda (sentence n-tracks machine-roles)
			  (sentence-derivatives
			   paras sentence machine-roles boxes-movie-id boxes-movie flow-movie
			   n-tracks (first box-scores) (second box-scores) video-scale
			   statistics model-names model-pos model-n-roles))
		         sentences n-tracks-multiple machine-roles-multiple))
		   (else (panic "gt-estimation-one: gt-mode invalid")))
	     (map (const '#()) sentences)))
	;; use the chain rule to obtain the derivative of the objective function
	(derivatives
	 (if ad?
	     (let ((likelihoods-linear (map exp likelihoods)))
	      (when (zero? (first likelihoods-linear)) (panic "gt-estimation-one"))
	      (v- (v/k (first derivatives-multiple) (first likelihoods-linear))
		  (v/k (reduce v+ derivatives-multiple
			       (make-vector (vector-length paras) 0))
		       (sum-f identity likelihoods-linear))))
	     '#())))  ;; use *cached-derivatives* because we only adjusted log-d  
  (when (some nan? (vector->list derivatives))
   (format #t "Check NUMBER_DIRECTIONS in hmm-likelihood-misc-AD.h,
               the maximal number of directions ~%")
   (panic "NAN detected in derivatives ~%"))
  derivatives))

(define (smooth-zero x)
 (if (zero? x) 1e-300 x))

(define (psi->model-linear psi)
 (let ((model (allocate-model (psi-ii psi) (psi-uu psi))))
  (set-model-name! model (symbol->c-string (psi-name psi)))
  (set-model-pos! model (psi-part-of-speech psi))
  (set-model-n-roles! model (psi-n-roles psi))
  (do ((i 0 (+ i 1))) ((= i (psi-ii psi)))
   (case (first (matrix-ref (psi-parameters psi) 0 i))
    ((continuous) (set-model-feature-type-continuous! model i 100.0))
    ((radial) (set-model-feature-type-radial! model i))
    ((discrete) (set-model-feature-type-discrete! model i))
    (else (panic "Unrecognized feature type"))))
  (do ((u 0 (+ u 1))) ((= u (psi-uu psi)))
   (set-model-b-linear! model u (vector-ref (psi-b psi) u))
   (do ((v 0 (+ v 1))) ((= v (psi-uu psi)))
    (set-model-a-linear! model u v (matrix-ref (psi-a psi) u v)))
   (do ((i 0 (+ i 1))) ((= i (psi-ii psi)))
    (let ((parameters (matrix-ref (psi-parameters psi) u i)))
     (case (first parameters)
      ((continuous)
       (set-model-parameter! model u i 0 (second parameters))
       (set-model-parameter! model u i 1 (third parameters)))
      ((radial)
       (set-model-parameter! model u i 0 (second parameters))
       (set-model-parameter! model u i 1 (third parameters)))
      ((discrete)
       (set-model-parameter! model u i 0 (length (rest parameters)))
       (for-each-indexed
 	(lambda (parameter j)
 	 (set-model-parameter! model u i (+ j 1) parameter))
 	(rest parameters)))
      (else (panic "Unrecognized feature type"))))))
  model))

(define (sentence-parts-of-speech sentence cfg semantics)
 (removeq
  'd
  (remove-duplicates 
   (map first (theta-role-assignments sentence cfg semantics)))))
 
(define (auto-d derivatives paras statistics)
 (define (decide-d lst ps)
  (let ((res (apply min (first (unzip (remove-if
				       (lambda (a) (zero? (second a)))
				       (zip lst ps)))))))
   (if (> res 0) 0 (+ (- res) (- (d-delta))))))
 
 (let loop ((d '())
	    (paras (vector->list paras))
	    (derivatives (vector->list derivatives))
	    (statistics statistics))
  (if (null? paras)
      (begin
       (unless (and (null? derivatives) (null? statistics))
	(panic "auto-d"))
       d)
      (let* ((data (first statistics))
	     (uu (first data))
	     (ii (second data))
	     (nns (third data))
	     (new-d
	      (join
	       (append 
		(map-n (lambda (u)
			(map-n
			 (const (decide-d (sublist derivatives (* u uu) (* (+ u 1) uu))
					  (sublist paras (* u uu) (* (+ u 1) uu))))
			 uu))
		       (+ uu 1))
		(map-n (lambda (u)
			(join 
			 (map-n (lambda (i)
				 (let ((start (+ uu (* uu uu)
						 (* u (reduce + nns 0))
						 (reduce + (take-if-possible i nns) 0)))
				       (end (+ uu (* uu uu)
					       (* u (reduce + nns 0))
					       (reduce + (take-if-possible (+ i 1) nns) 0))))
				  (map-n (const (decide-d (sublist derivatives start end)
							  (sublist paras start end)))
					 (list-ref nns i))))
				ii)))
		       uu)))))
       (loop (append d new-d)
	     (drop (+ uu (* uu uu) (* uu (reduce + nns 0))) paras)
	     (drop (+ uu (* uu uu) (* uu (reduce + nns 0))) derivatives)
	     (rest statistics))))))

(define (discrimination-with-sentence-likelihood video boxes-movie flow-movie sentence negatives models)
 (let* ((competition-set (cons sentence negatives))
	;; Call c function to speed up	
	(likelihoods (sentence-likelihoods-one-video-with-computed-boxes
		      video competition-set *model-constraint* models boxes-movie flow-movie #f))
	(total (reduce my-add-exp1 likelihoods minus-infinity)))
  (when (verbose?)
   (format #t "~a ~a ~% ~a ~%"
	   (length likelihoods)
	   (list (first likelihoods)
	   	 (reduce my-add-exp1 (rest likelihoods) minus-infinity))
	   (- (first likelihoods) total)))
  (list (- (first likelihoods) total)
	likelihoods)))

(define (discrimination-with-sentence-likelihood-in-sc
	 boxes-movie-id video boxes-movie flow-movie sentence negatives models)
 (let* ((competition-set (cons sentence negatives))
	(box-scores (memoized-box-scores boxes-movie-id boxes-movie flow-movie (get-scale video)))
	(parsing-results
	 (map (lambda (s)
	       (sentence:sentence->participants-roles-and-state-machines
		s *cfg* *semantics* models))
	      competition-set))
	(machine-roles-multiple (map third parsing-results))
	(n-tracks-multiple (map (lambda (pr) (+ (maximum (reduce append (third pr) '())) 1))
				parsing-results))
	(sentences (map (lambda (pr)
			 (map (o model->psi-log trained-hmm-model) (first pr)))
			parsing-results))
	(likelihoods (map (lambda (s n-tracks roles)
			   (let* ((eligible-states (compute-eligible-states
						    n-tracks roles boxes-movie *model-constraint*))
				  (track-score
				   (memoized-track-score boxes-movie-id roles eligible-states
							 (first box-scores) (second box-scores))))
			    (sentence-likelihood-and-prior
			     boxes-movie-id s roles boxes-movie flow-movie track-score
			     (first eligible-states) (second eligible-states)
			     (first box-scores) (second box-scores) (get-scale video))))
			  sentences n-tracks-multiple machine-roles-multiple))
	(total (reduce my-add-exp1 likelihoods minus-infinity)))
  (when (verbose?)
   (format #t "~a ~a ~% ~a ~%"
	   (length likelihoods)
	   (list (first likelihoods)
	   	 (reduce my-add-exp1 (rest likelihoods) minus-infinity))
	   (- (first likelihoods) total)))
  (list (- (first likelihoods) total)
	likelihoods)))

(define (gt-estimation-multiple last-objective-value videos flow-movies boxes-movies
				sentences negatives-multiple current-models scratch-models d)
 (let* (;;; First calculate discriminations useing c function
	(discriminations-and-likelihoods-multiple
	 (time-code
	  (if d
	      (list (list *cached-objective-value*)
		    (map-n (const #f) (length sentences)))
	      (unzip
	       (map-indexed
		(lambda (sentence-negatives-and-boxes-movie boxes-movie-id)
		 (let* ((sentence (first sentence-negatives-and-boxes-movie))
			(negatives (second sentence-negatives-and-boxes-movie))
			(boxes-movie (third sentence-negatives-and-boxes-movie))
			(idx (second sentence))
			(sentence (first sentence)))
		  (discrimination-with-sentence-likelihood
		   (list-ref videos idx) boxes-movie
		   (list-ref flow-movies idx) sentence negatives current-models)))
		(zip sentences negatives-multiple boxes-movies))))))
	(current-objective-value (reduce + (first discriminations-and-likelihoods-multiple) 0))
	(likelihoods-multiple (second discriminations-and-likelihoods-multiple)))
  
  (let ((cnt-paras (first (pack (map trained-hmm-model current-models)))))
   (format #t "Parameters difference with last ~a ~%Parameters difference with init: ~a ~%"
	   (distance cnt-paras *last-paras*)
	   (distance cnt-paras *init-paras*)))
  
  ;; 1. check objective value
  (if (< current-objective-value last-objective-value)
      (begin
       (format #t "---- Currently decreased to: discrimination ~a ---- ~%"
	       current-objective-value)
       (format #t "~a ~%" return)
       (list 'objective-decrease current-objective-value d))
      (let* ((current-models-ptrs (map trained-hmm-model current-models))
	     (model-names (map model-name current-models-ptrs))
	     (model-pos (map model-pos current-models-ptrs))
	     (model-n-roles (map model-n-roles current-models-ptrs))
             ;;; Pack model parameters first
	     (packed (pack current-models-ptrs))
	     (paras (first packed))
	     (statistics (second packed))
	     (derivatives-multiple
	      (time-code
	       (map-indexed
		(lambda (snbl boxes-movie-id)
		 (let* ((sentence (first snbl))
			(negatives (second snbl))
			(boxes-movie (third snbl))
			(likelihoods (fourth snbl))
			(idx (second sentence))
			(sentence (first sentence)))
		  (gt-estimation-one (list-ref videos idx) boxes-movie-id
				     likelihoods (list-ref flow-movies idx)
				     boxes-movie sentence negatives current-models paras
				     statistics model-names model-pos model-n-roles (not d))))
		(zip sentences negatives-multiple boxes-movies likelihoods-multiple))))
	     (derivatives
	      (if d
		  *cached-derivatives*
		  (reduce v+ derivatives-multiple
			  (make-vector (vector-length paras) 0))))
	     (d (if d (map exp d) (auto-d derivatives paras statistics)))
	     (new-paras 
	      (map-vector (lambda (para derivative damping)
			   (let ((value (* para (+ derivative damping))))
			    (if (or (zero? para) (zero? (abs value)))
				0
				value)))
			  paras derivatives (list->vector d)))
	     (new-psis (unpack new-paras statistics model-names model-pos model-n-roles))
	     ;; Since psi->model will take the log
	     (tmp-models (map psi->model-linear new-psis))
	     (return 'continue))
       
       ;; Update scratch models
       (for-each (lambda (tmp-model scratch-model)
		  (copy-model! (trained-hmm-model scratch-model) tmp-model))
		 tmp-models scratch-models)
       (for-each free-model tmp-models)

       ;; For the models not updated (i.e., scratch model is zero),
       ;; we simply copy the current models
       (for-each (lambda (cm-sm)
		  (when (zero-model? (trained-hmm-model (second cm-sm)))
		   (copy-model! (trained-hmm-model (second cm-sm))
				(trained-hmm-model (first cm-sm)))
		   ;; NOTE: Convert from log space to linear space
		   ;; The spaces that current models and scratch models reside in are DIFFERENT
		   (log->linear-model! (trained-hmm-model (second cm-sm)))))
		 (zip current-models scratch-models))
       
       ;; 2. check normalization
       (for-each
	(lambda (model)
	 ;; NOTE: normalize-model! will convert the model from linear space to log space
	 (when (zero? (normalize-model! (trained-hmm-model model)
					(if (upper-triangular?) 1 0)
					*training-mode*))
	  (set! return 'normalization-error)
	  (set! *cached-derivatives* derivatives)
	  (set! *cached-objective-value* current-objective-value)))
	scratch-models)
       ;; 3. check stop
       (when (< (abs (- current-objective-value last-objective-value)) (tolerance))
       	(set! return 'stop))
       ;; Display
       (when (or (equal? return 'stop) (equal? return 'continue))
	(set! *cached-derivatives* derivatives)
	(set! *cached-objective-value* current-objective-value)
	(set! *last-paras* (first (pack (map trained-hmm-model current-models))))
	(format #t "Currently increased to: discrimination ~a ~%" current-objective-value))
       (format #t "~a ~%" return)
       (list return current-objective-value (map log d))))))

(define (sentence-training-one
	 video flow-movie boxes-movie sentence negatives current-models scratch-models d)
 (let* ((competition-set (cons sentence negatives))
	;; For each sentence in the set, parse it
	;; Each parsing result should be in the format:
	;; (hmms
	;;  role-participant-pair
	;;  roles-for-machines
	;;  theta-roles)
	(parsing-results
	 (map (lambda (s)
	       (sentence:sentence->participants-roles-and-state-machines
		s *cfg* *semantics* current-models))
	      competition-set))
	;; Get the pointers for hmm models
	(sentences (map (lambda (pr)
			 (map trained-hmm-model (first pr)))
			parsing-results))
	(scratch-sentences (map (lambda (hmms)
				 (map (lambda (hmm) 
				       (trained-hmm-model (find-if (lambda (scratch)
								    (equal? (trained-hmm-name hmm)
									    (trained-hmm-name scratch)))
								   scratch-models)))
				      hmms))
				(map first parsing-results)))
	;; Get the hmms roles
	(machine-roles (map third parsing-results))
	(n-tracks (map (lambda (pr) (+ (maximum (reduce append (third pr) '())) 1))
		       parsing-results)))
  (unless (every (lambda (boxes) (= (length (first boxes-movie))
				    (length boxes)))
		 boxes-movie)
   (panic "sentence-training-one: ~a ~% boxes-movie with unequal lengths" video))
  
  ;; Call estimation function in C here
  (let* ((sentences (map (lambda (s)
			  (with-c-pointers
			   (lambda (s-array)
			    (initialize-sentence (length s) s-array))
			   (list->vector s)))
			 sentences))
	 (scratch-sentences (map (lambda (s)
				  (with-c-pointers
				   (lambda (s-array)
				    (initialize-sentence (length s) s-array))
				   (list->vector s)))
				 scratch-sentences))
	 (machine-roles
	  (map (lambda (roles)
		(map (lambda (r)
		      (let ((r-vec (allocate-ivec (length r))))
		       (for-each-indexed (lambda (x i) (set-ivec! r-vec i x)) r)
		       r-vec))
		     roles))
	       machine-roles))
	 (c-machine-roles (map (lambda (roles)
				(let ((array (malloc (* (length roles) c-sizeof-s2cuint))))
				 (list->c-array array roles c-s2cuint-set! c-sizeof-s2cuint)))
			       machine-roles))
	 (c-flow-struct-movie (map (lambda (fl) (optical-flow->c-flow-struct fl)) flow-movie))
	 (c-boxes-movie (map (lambda (boxes-per-frame)
			      (map voc4-detection->box boxes-per-frame))
			     boxes-movie))
	 (c-boxes-movie-struct (allocate-boxes-movie (length c-boxes-movie)
						     (length (first c-boxes-movie))))
	 (_ (for-each-indexed
	     (lambda (boxes-per-frame t)
	      (begin
	       (unless (= (length boxes-per-frame) (* (length *model-groups*) (top-n)))
		(panic "sentence-training-one: box number error ~a" (length boxes-per-frame)))
	       (for-each-indexed
		(lambda (box i)
		 (set-boxes-movie! c-boxes-movie-struct t i box))
		boxes-per-frame)))
	     c-boxes-movie))
	 (c-update-results
	  (with-c-pointers
	   (lambda (ss-array)
	    (with-c-pointers
	     (lambda (sss-array)
	      (with-vector->c-exact-array
	       (lambda (n-tracks-array)
		(with-c-pointers
		 (lambda (machine-roles-array)
		  (with-c-pointers
		   (lambda (c-flow-struct-array)
		    (with-c-strings
		     (lambda (objects-array)
		      (with-c-pointers
		       (lambda (c-feature-medoids-array)
			(update-x! ss-array sss-array n-tracks-array (length n-tracks) machine-roles-array
				   c-boxes-movie-struct c-flow-struct-array (length flow-movie) (get-scale video)
				   d *training-mode* objects-array (length *objects*) c-feature-medoids-array
				   (length *c-feature-medoids*) *model-constraint*))
		       (list->vector *c-feature-medoids*)))
		     (list->vector *objects*)))
		   (list->vector c-flow-struct-movie)))
		 (list->vector c-machine-roles)))
	       c-sizeof-int
	       (list->vector n-tracks)
	       #t))
	     (list->vector scratch-sentences)))
	   (list->vector sentences)))
	 (discrimination (update-results-discrimination c-update-results))
	 (likelihood (update-results-likelihood c-update-results)))
   (for-each free-sentence sentences)
   (for-each free-sentence scratch-sentences)
   (for-each free c-machine-roles)
   (for-each (lambda (roles) (for-each free-ivec roles)) machine-roles)
   (for-each free-c-flow-struct c-flow-struct-movie)
   (for-each (lambda (boxes) (for-each free-box boxes)) c-boxes-movie)
   (free-boxes-movie c-boxes-movie-struct)
   (free c-update-results)
   (list likelihood discrimination))))

(define (sentence-training-multiple last-objective-value videos flow-movies boxes-movies
				    sentences negatives-multiple current-models scratch-models d)
 (let ((likelihood-and-discriminations
	(map
	 (lambda (sentence negatives boxes-movie)
	  (let ((sentence (first sentence))
		(idx (second sentence)))
	   (time-code
	    (sentence-training-one (list-ref videos idx)
				   (list-ref flow-movies idx)
				   boxes-movie sentence negatives
				   current-models scratch-models (first d)))))
	 sentences negatives-multiple boxes-movies)))
  (newline)
  ;; For the models not updated (i.e., scratch model is zero),
  ;; we simply copy the current models
  (for-each (lambda (cm-sm)
	     (when (zero-model? (trained-hmm-model (second cm-sm)))
	      (copy-model! (trained-hmm-model (second cm-sm))
			   (trained-hmm-model (first cm-sm)))
	      ;; NOTE: Convert from log space to linear space
	      ;; The spaces that current models and scratch models reside in are DIFFERENT
	      (log->linear-model! (trained-hmm-model (second cm-sm)))))
	    (zip current-models scratch-models))
  ;; Now normalize all the updated models while checking the legality of the current update
  ;; The order 12 and 13 shouldn't be changed, i.e.,
  ;; priority 2 > 1 and 3 > 1. 2 and 3 are exclusive
  (let* ((return 'continue)
	 (values (map (lambda (l)
		       (sum-f identity l))
		      (unzip likelihood-and-discriminations)))
	 (current-objective-value (if (training-mode-ml? *training-mode*)
				      (first values)
				      (second values)))
	 (current-auxilary-value (if (training-mode-ml? *training-mode*)
				     (second values)
				     (first values))))
   ;; 1. check normalization
   (call-with-current-continuation
    (lambda (k)
     (for-each
      (lambda (model)
       ;; NOTE: normalize-model! will convert the model from linear space to log space
       (when (zero? (normalize-model! (trained-hmm-model model)
				      (if (upper-triangular?) 1 0)
				      *training-mode*))
	(set! return 'normalization-error)
	(k #f)))
      scratch-models)))
   ;; 2. check objective value
   (when (< current-objective-value last-objective-value)
    (format #t "current : ~a ~%" current-objective-value)
    (set! return 'objective-decrease))
   ;; 3. check stop
   (when (< (abs (- current-objective-value last-objective-value)) (tolerance))
    (set! return 'stop))
   ;; Display
   (when (or (equal? return 'stop) (equal? return 'continue))
    (format #t (if (training-mode-ml? *training-mode*)
		   "Current: likelihood ~a discrimination ~a ~%"
		   "Current: discrimination ~a likelihood ~a ~%")
	    current-objective-value current-auxilary-value))
   (format #t "~a ~%" return)
   (list return current-objective-value d))))

(define (sentence-training-iterative-one videos flow-movies
					 boxes-movies sentences initialized-models)
 ;; (write-object-to-file
 ;;  (map (lambda (model)
 ;; 	(model->psi #f (trained-hmm-model model)))
 ;;       initialized-models)
 ;;  "/home/yu239/tmp/initialized-models.sc")  ;; for debugging
 
 (set! *init-paras* (first (pack (map trained-hmm-model initialized-models))))
 
 (define (evolution-d d last-d type)
  (case type
   ((continue)
    (set! n-continuous-success (+ n-continuous-success 1))
    (if (equal? *training-multiple-f*
		gt-estimation-multiple)
	#f
	(if (= n-continuous-success (d-step))
	    (begin (set! n-continuous-success 0)
		   (map (lambda (damping) (+ damping (d-delta))) d))
	    d)))
   ((normalization-error)
    (set! n-continuous-success 0)
    (map (lambda (damping) (max (- damping (d-delta)) 0)) d))
   ((objective-decrease)
    (set! n-continuous-success 0)
    (map (lambda (damping) (max (- damping (d-delta)) 0)) last-d))
   (else (fuck-up))))
 
 (define n-continuous-success 0)

 (let* ((negatives-multiple
	 (if *stored-negatives-multiple*
	     *stored-negatives-multiple*
	     (map (lambda (s)
		   (generate-negatives *negatives-f* (first s)
				       (stand-alone-video->string (list-ref videos (second s)))
				       *cfg* *semantics* *training-mode*))
		  sentences)))
	(_ ;; Do the smoothing in case that the initial likelihood for a positive sample is -inf
	 (for-each (lambda (m)
		    (smooth-model! (trained-hmm-model m)
				   (if (upper-triangular?) 1 0)
				   1e-50))
		   initialized-models)))
  
  (let loop ((iteration 1)
	     (last-models (map clone-model initialized-models))
	     (current-models initialized-models)
	     (scratch-models (map clone-model initialized-models))
	     (last-objective-value minus-infinity)
	     (d (if (equal? *training-multiple-f*
			    gt-estimation-multiple)
		    #f
		    (list (initialized-d))))
	     (last-d #f))
   
   ;; Make sure that each scratch model has the same state number with current model,
   ;; in case that some unreachable states were removed when training
   (for-each (lambda (cm-sm)
	      (let ((cm (trained-hmm-model (first cm-sm)))
		    (sm (trained-hmm-model (second cm-sm))))
	       (unless (= (model-uu cm) (model-uu sm))
		(free-model sm)
		(set-trained-hmm-model!
		 (second cm-sm)
		 (let ((m (allocate-model (model-ii cm) (model-uu cm))))
		  (copy-model! m cm)
		  m)))))
	     (zip current-models scratch-models))
   ;; Zero scratch models
   (for-each (o zero-model! trained-hmm-model) scratch-models)
   
   (let* ((res (*training-multiple-f* last-objective-value videos flow-movies
				      boxes-movies sentences negatives-multiple
				      current-models scratch-models d))
	  (return (first res))
	  (objective-value (second res))
	  (returned-d (third res)))
    (format #t "Iteration ~a completed, average returned-d ~a ~%~%~%"
	    iteration (if returned-d (list-mean returned-d) returned-d))
    (cond
     ((or (equal? return 'stop)
	  (and (not (equal? return 'objective-decrease))
	       (or (and returned-d
			(every (lambda (damping) (> damping (maximum-d))) returned-d))
		   (> iteration (- (maximum-iteration) *stored-n-iteration*)))))
      (for-each (o free-model trained-hmm-model) last-models)
      (for-each (o free-model trained-hmm-model) scratch-models)
      (format #t "Iteration completed as ~a ~%"
	      (cond ((equal? return 'stop) "tolerance reached")
		    ((> iteration (- (maximum-iteration) *stored-n-iteration*)) "maximum number of iterations reached")
		    (else "d reached the maximum")))
      (list objective-value current-models))
     ((equal? return 'continue)
      ;;; For record, output current models every iteration when succeed
      (write-object-to-file
       (list objective-value
	     (map (lambda (model)
		   (model->psi #f (trained-hmm-model model)))
		  current-models)
	     negatives-multiple
	     *phase*
	     iteration)
       (result-path))
      (loop (+ iteration 1) current-models scratch-models last-models
	    objective-value (evolution-d returned-d last-d return) returned-d))
     ((equal? return 'normalization-error)
      (loop (+ iteration 1) last-models current-models scratch-models
	    last-objective-value (evolution-d returned-d last-d return) returned-d))
     ((equal? return 'objective-decrease)
      (loop (+ iteration 1) scratch-models last-models current-models
	    minus-infinity (evolution-d returned-d last-d return) returned-d))
     (else (fuck-up)))))))

(define (sentence-training-iterative-multiple videos flow-movies boxes-movies sentences models)
 (when (null? sentences) (panic "sentences empty!"))
 (when (verbose?)
  (for-each-indexed
   (lambda (sentence i)
    (let ((video (list-ref videos (second sentence)))
	  (sentence (first sentence)))
     (begin
      (format #t "-------------------------------~%")
      (format #t "Video-sentence training pair:~a~%" i)
      (format #t "Video: ~a~%" video)
      (format #t "Sentence: ~a~%" sentence)
      (format #t "Video length: ~a~%" (video-length video)))))
   sentences))
 ;; Initialize hash tables
 (set! *box-scores-table* (create-hash-table))
 (set! *track-scores-table* (create-hash-table))
 (set! *features-tables* (map-n (lambda _ (create-hash-table)) (length sentences)))

 (let loop ((remaining (restart-times))
	    (best minus-infinity)
	    (best-models #f))
  (format #t "========== Remaining trials: ~a ============ ~%" remaining)
  (format #t "Current best objective value: ~a ~%" best)
  (if (zero? remaining)
      (list best best-models)
      (let* ((initialized-models (map (lambda (model)
				       (let ((new-model (clone-model model)))
					(*initializing-f* (trained-hmm-model new-model))
					new-model)) models))
	     (old-training-mode *training-mode*)
	     (value-and-models
	      (if (= (n-phases) 2)
		  ;; If it's two-stage training
		  ;; First train just verbs and nouns
		  ;; Then train other words
		  (let* ((sentences-and-boxes-movies (zip sentences boxes-movies))
			 (short-sentences-and-boxes-movies
			  (unzip
			   (remove-if-not (lambda (sb)
					   (= (length (removeq
						       'p  ;; some verbs have prepositions with them
						       (sentence-parts-of-speech
							(first (first sb)) *cfg* *semantics*))) 2))
					  sentences-and-boxes-movies)))
			 (short-sentences (first short-sentences-and-boxes-movies))
			 (short-boxes-movies (second short-sentences-and-boxes-movies))
			 (phase1-trained-result
			  (begin
			   (set! *training-mode* (if (training-mode-ml? *training-mode*)
						     training-mode-c:ml
						     training-mode-c:dt))
			   (set! *training-multiple-f* (if (training-mode-ml? *training-mode*)
							   sentence-training-multiple
							   gt-estimation-multiple))
			   (set! *phase* 1)
			   (if (or (not *stored-phase*) (= 1 *stored-phase*))
			       (sentence-training-iterative-one
				videos flow-movies short-boxes-movies short-sentences initialized-models)
			       #f))))
		   (set! *training-mode* training-mode-c:ml)
		   (set! *training-multiple-f* sentence-training-multiple)
		   (set! *phase* 2)
		   (sentence-training-iterative-one
		    videos flow-movies boxes-movies sentences
		    (if (and *stored-phase* (= 2 *stored-phase*))
			initialized-models
			(begin
			 (set! *stored-negatives-multiple* #f)
			 (set! *stored-n-iteration* 0)
			 (second phase1-trained-result)))))
		  ;; Otherwise
		  (begin
		   (set! *phase* 1)
		   (sentence-training-iterative-one videos flow-movies
						    boxes-movies sentences initialized-models))))
	     (_ (set! *training-mode* old-training-mode)))
       
       (if (> (first value-and-models) best)
	   (begin (and best-models (for-each (o free-model trained-hmm-model) best-models))
		  (loop (- remaining 1) (first value-and-models) (second value-and-models)))
	   (begin (for-each (o free-model trained-hmm-model) (second value-and-models))
		  (loop (- remaining 1) best best-models)))))))

(define-command
 (main
  (any-number ("m" model-groups?
	       (model-groups "model-group" string-argument)))
  (any-number ("pos" part-of-speech?
	       (parts-of-speech "parts-of-speech" string-argument)))
  (at-most-one ("negatives-replace-nouns" negatives-replace-nouns?)
	       ("negatives-replace-verbs" negatives-replace-verbs?)
	       ("negatives-replace-all" negatives-replace-all?))
  (at-most-one ("constant-model" constant-model?)
	       ("uniform-model" uniform-model?)
	       ("randomise-model" randomise-model?)
	       ("noise-model" noise-model?)
	       ("file-model" file-model?
		(model-file "pathname" string-argument "")))
  (at-most-one ("the-306-corpus-cfg" the-306-corpus-cfg?)
	       ("new3-corpus-cfg" new3-corpus-cfg?)
	       ("new4-corpus-cfg" new4-corpus-cfg?))
  (at-most-one ("annotation-path" annotation-path?
		(annotation-path "pathname" string-argument "")))
  (at-most-one ("ml" maximum-likelihood?
		(n-phases "number" integer-argument 1))
	       ("dt" discriminative-training?
		(dt-mode "dt-mode" string-argument "gt-in-c")
		(negatives-n "number" integer-argument 47))
	       ("mixed" mixed-training?
		(mixed-dt-mode "mixed-dt-mode" string-argument "gt-in-c")
		(mixed-negatives-n "number" integer-argument 47)))
  (at-most-one ("prediction-lookahead" lookahead?
		(prediction-lookahead "prediction" integer-argument 20)))
  (at-most-one ("model-path" model-path?
		(model-path "pathname" string-argument
			    (string-append (getenv "HOME") ""))))
  (at-most-one ("top-n" top-n?
		(top-n "number" integer-argument 5)))
  (at-most-one ("restart-times" restart-times?
		(restart-times "restarts" integer-argument 5)))
  (at-most-one ("noise-delta" noise-delta?
		(noise-delta "delta" real-argument 0.5)))
  (at-most-one ("tolerance" tolerance?
		(tolerance "tolerance" real-argument 1e-4)))
  (at-most-one ("d-delta" d-delta?
		(d-delta "d-delta" real-argument (log 0.5))))
  (at-most-one ("d-step" d-step?
		(d-step "d-step" integer-argument 10)))
  (at-most-one ("maximum-d" maximum-d?
		(maximum-d "maximum-d" real-argument 20.0)))
  (at-most-one ("initialized-d" initialized-d?
		(initialized-d "init-d" real-argument 3.0)))
  (at-most-one ("maximum-iteration" maximum-iteration?
		(maximum-iteration "n-iteration" integer-argument infinity)))
  (at-most-one ("upper-triangular" upper-triangular?))
  (at-most-one ("compute-likelihood" compute-likelihood?
		(sentence "sentence" string-argument "")
		(video "video" string-argument "")
		(write-path "path" string-argument "")))
  (at-most-one ("verbose" verbose?))
  (at-most-one ("no-model-constraint" no-model-constraint?) ;; by default
	       ("no-duplicate-models" no-duplicate-models?))
  (at-most-one ("start-from-last-time" start-from-last-time?)
	       ("start-from-scratch" start-frome-scratch?))
  (at-most-one ("result-path" result-path?
		(result-path "path" string-argument "/tmp/sentence-training-results-XXXXXX.sc")))
  (at-most-one ("video-directory" video-directory?
		(video-directory "directory" string-argument #f)))
  (at-most-one ("training-samples" training-samples?
		(training-samples-file "training-samples-file" string-argument #f))))
 (unless model-groups?
  (panic "Must specify at least one model group"))
 
 (when (and (not (subset? parts-of-speech '("n" "v"))) discriminative-training?)
  (panic "not suggest discriminative training for all parts-of-speech, try mixed-training"))
 
 (set! *model-groups* (map (lambda (group) (pregexp-split "," group))
			   model-groups))
 (set! *cfg*
       (cond (new3-corpus-cfg? *new3-corpus:cfg*)
	     (new4-corpus-cfg? *new4-corpus:cfg*)
	     (else (panic "Must specify a language"))))
 (set! *semantics*
       (cond (new3-corpus-cfg? *new3-corpus:semantics*)
	     (new4-corpus-cfg? *new4-corpus:semantics*)
	     (else (panic "Must specify a language"))))
 (set! *objects*
       (cond (new3-corpus-cfg? *new3-corpus:objects*)
	     (new4-corpus-cfg? *new4-corpus:objects*)
	     (else (panic "Must specify a language"))))
 (set! *feature-medoids*
       (cond (new3-corpus-cfg? *new3-corpus:feature-medoids*)
	     (new4-corpus-cfg? *new4-corpus:feature-medoids*)
	     (else (panic "Must specify a language"))))
 
 (set! *c-feature-medoids* (map (lambda (fm)
				 (feature-medoid->c-feature-medoid fm))
				*feature-medoids*))
 (set! *model-constraint* (if no-duplicate-models?
			      model-constraint:no-duplicates
			      model-constraint:none))
 (set! *parameters* (make-global-option
		     prediction-lookahead
		     model-path
		     result-path
		     top-n
		     (if (or mixed-training? (= n-phases 2)) 2 1)
		     restart-times
		     noise-delta
		     tolerance
		     (if mixed-training? mixed-dt-mode dt-mode)
		     d-delta
		     d-step
		     maximum-d
		     initialized-d
		     maximum-iteration
		     (if mixed-training? mixed-negatives-n negatives-n)
		     upper-triangular?
		     verbose?))
 (set! *video-directory* video-directory)
 (when file-model?
  (unless (file-exists? model-file)
   (panic "Model file does not exist")))

 (set! *models*
       (cond
	(file-model? (map psi->model (read-object-from-file model-file)))
	(start-from-last-time?
	 (unless (file-exists? result-path)
	  (panic "Invalid result-path provided!"))
	 (map psi->model (second (read-object-from-file result-path))))
	(else #f)))
 
 (if compute-likelihood?
     ;; Sentence likelihood
     (let ((_ (unless file-model? (panic "compute-likelihood only supports input file models")))
	   (likelihood
	    (sentence-likelihood (make-stand-alone-video (string-append video-directory "/" video))
				 sentence model-constraint:none *models* #t #t write-path)))
      (when *c-feature-medoids*
       (for-each free-feature-medoid *c-feature-medoids*))
      likelihood)
     ;; Sentence training
     (begin
      (unless (and part-of-speech? (subset? '("n" "v") parts-of-speech))
       (panic "Part of speeches error"))
      (set! *sentence-annotation*
	    (cond ((and new4-corpus-cfg? (not maximum-likelihood?) (not annotation-path?))
		   (panic "new4 requires sentence annotation for generating negatives"))
		  ((and new4-corpus-cfg? (not maximum-likelihood?))
		   (read-object-from-file annotation-path))
		  (else #f)))
      (set! *negatives-f*
	    (cond (negatives-replace-nouns?
		   (lambda (sentence cfg semantics video-name)
		    (negatives-replace-words sentence video-name cfg semantics '(n))))
		  (negatives-replace-verbs?
		   (lambda (sentence cfg semantics video-name)
		    (negatives-replace-words sentence video-name cfg semantics '(n v))))
		  (negatives-replace-all?
		   (lambda (sentence cfg semantics video-name)
		    (negatives-replace-words sentence video-name cfg semantics '(n v a p pm adv))))
		  (else (panic "Must specify a negative generating function"))))
      (set! *initializing-f*
	    (cond (constant-model? identity)
		  (uniform-model?
		   (lambda (m) (uniform-model! m upper-triangular?)))
		  (randomise-model?
		   (lambda (m) (randomise-model! m upper-triangular?)))
		  (noise-model?
		   (lambda (m) (noise-model! m upper-triangular? noise-delta)))
		  ((or file-model? start-from-last-time?)
		   (lambda (m)
		    (copy-model! m (find-if
				    (lambda (fm)
				     (equal? (model-name fm) (model-name m)))
				    *models*))))
		  (else (panic ("Must specify a model initializing function")))))
      (set! *training-multiple-f*
	    (cond ((or maximum-likelihood? (and discriminative-training?
						(equal? dt-mode "ebw")))
		   sentence-training-multiple)
		  ((or discriminative-training? mixed-training?) gt-estimation-multiple)
		  (else (panic "Must specify a training function"))))
      (set! *training-mode* (cond
			     (maximum-likelihood? training-mode-c:ml)
			     (discriminative-training? training-mode-c:dt)
			     (mixed-training? training-mode-c:mixed)
			     (else (panic "Must specify a training mode"))))
      (when start-from-last-time?
       (let ((saving (read-object-from-file result-path)))
	(set! *stored-negatives-multiple* (third saving))
	(set! *stored-phase* (fourth saving))
	(set! *stored-n-iteration* (fifth saving))))
      ;;; Pre-processing: read all the data
      (let* ((pos (map c-string->symbol parts-of-speech))
	     (videos-and-sentences (unzip (read-object-from-file training-samples-file)))
	     (videos (map (lambda (v) (make-stand-alone-video
				       (string-append video-directory "/" v)))
			  (first videos-and-sentences)))
	     (sentences
	      (reduce append
		      (map-indexed (lambda (lst i)
				    (remove-if-not
				     identity
				     (map (lambda (s)
					   (if (and (subset?
						     (map pos-generalize
							  (sentence-parts-of-speech s *cfg* *semantics*))
						     pos)
						    (not (substring? "object" s)))
					       (list s i)
					       #f)) lst)))
				   (second videos-and-sentences))
		      '()))
	     (flow-movies (map read-optical-flow-movie-in-c videos))
	     (detector-boxes-movies-multiple
	      (map-indexed (lambda (video i)
			    (begin
			     (format #t "Reading boxes for video ~a ~%" i)
			     (map (lambda (model-name)
				   (read-voc4-detector-boxes video model-name))
				  (join *model-groups*))))
			   videos))
	     (processed-tracks-multiple
	      (map (lambda (sentence)
		    (let* ((idx (second sentence))
			   (sentence (first sentence))
			   (video (list-ref videos idx))
			   (flow-movie (list-ref flow-movies idx))
			   (detector-boxes-movies (list-ref detector-boxes-movies-multiple idx)))
		     (map
		      (lambda (model-group)
		       (first
			(viterbi-prepare-tracks
			 (get-scale video) (video-dimensions video) (video-length video)
			 flow-movie
			 (map (lambda (model-name)
			       (let* ((boxes-movie-and-name
				       (find-if (lambda (x) (equal? model-name (second x)))
						(zip detector-boxes-movies (join *model-groups*))))
				      (boxes-movie (first boxes-movie-and-name))
				      (name (second boxes-movie-and-name))
				      (boxes-movie
				       (if (and
					    new3-corpus-cfg?
					    (not (substring? "object" sentence))
					    (not (substring? model-name sentence))
					    (not (equal? model-name "person")))
					   (map (lambda (boxes)
						 (let ((drop-number (min 10 (- (length boxes) top-n))))
						  (if (positive? drop-number)
						      (drop drop-number
							    (sort boxes > voc4-detection-strength))
						      boxes)))
						boxes-movie)
					   boxes-movie)
				       ))
				(list boxes-movie name)))
			      model-group)
			 top-n
			 model-path
			 *model-threshold-tracker-offset*
			 *profile-best-boxes?*
			 prediction-lookahead)))
		      *model-groups*)))
		   sentences))
	     (boxes-movies
	      (map (lambda (processed-tracks)
		    (map join (transpose-list-of-lists
			       (map (lambda (movie)
				     (map (lambda (boxes)
					   (pad-with-last-if-necessary boxes top-n)) movie))
				    (reselect-topn-boxes processed-tracks top-n)))))
		   processed-tracks-multiple))
	     (flow-movies (map (lambda (flow-movie detector-boxes-movies)
				(pad-with-last-if-necessary flow-movie
							    (length (first detector-boxes-movies))))
			       flow-movies detector-boxes-movies-multiple))
	     (lexicon (sort 
		       (remove-if (lambda (entry) (article? entry))
				  (map (lambda (entry)
					(pregexp-replace* " " (string-downcase entry) "-"))
				       (cfg:lexicalized-terminals *cfg*)))
		       string<?
		       identity))
	     (models (remove-if
		      (o negative? first)
		      (map (lambda (model)
			    (let ((entry (string-downcase (symbol->string (trained-hmm-name model)))))
			     (list (if (member entry lexicon) (position entry lexicon) -1)
				   model)))
			   (cond (the-306-corpus-cfg? (*306-corpus:hmms*))
				 (new3-corpus-cfg? (*new3-corpus:hmms*))
				 (new4-corpus-cfg? (*new4-corpus:hmms*))
				 (else (panic "Must specify a language"))))))
	     (models (map second (sort models < first))))
       (when verbose?
	(format #t "~a ~%" (global-option->pretty-string *parameters*))
	(format #t "model-groups: ~a ~%" *model-groups*)
	(format #t "training-mode: ~a ~%"
		(cond ((equal? *training-mode* training-mode-c:ml)
		       "maximum-likelihood")
		      ((equal? *training-mode* training-mode-c:dt)
		       "discriminative-training")
		      (else "mixed-training")))
	(format #t "video number: ~a ~%" (length videos)))
       (let ((result (sentence-training-iterative-multiple videos flow-movies
							   boxes-movies sentences models)))
	;; Clean up
	(for-each (lambda (flow-movie)
		   (for-each (lambda (flow) (free (c-optical-flow-handle flow)))
			     (but-last flow-movie)))
		  flow-movies)
	(when *c-feature-medoids* (for-each free-feature-medoid *c-feature-medoids*))
	(when *models* (for-each free-model *models*))
	;; Write result
	(write-object-to-file
	 (list
	  (first result)
	  (map (lambda (model)
		(model->psi #f (trained-hmm-model model)))
	       (second result))
	  '()
	  *phase*
	  maximum-iteration)
	 result-path)
	result)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;; End of sentence training ;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(define (sentence-likelihoods-one-video-with-computed-boxes
	 video plain-sentences model-constraint models
	 boxes-movie flow-movie final-state?)
 (let* ((parsing-results
	 (map (lambda (s)
	       (sentence:sentence->participants-roles-and-state-machines
		s *cfg* *semantics* models))
	      plain-sentences))
	(sentences (map (lambda (parsing-result)
			 (map trained-hmm-model (first parsing-result)))
			parsing-results))
	(roles-multiple (map (lambda (parsing-result) (third parsing-result))
			     parsing-results))
	(n-tracks-multiple
	 (map (lambda (parsing-result)
	       (+ (maximum (reduce append (third parsing-result) '())) 1))
	      parsing-results)))
  (unless (every (lambda (boxes) (= (length (first boxes-movie))
  				    (length boxes)))
  		 boxes-movie)
   (panic "sentence-likelihood-one-video-with-computed-boxes: ~a ~%
boxes-movie with unequal lengths" video))
  (let* ((sentences
	  (map (lambda (sentence)
		(with-c-pointers
		 (lambda (s-array)
		  (initialize-sentence (length sentence) s-array))
		 (list->vector sentence)))
	       sentences))
	 (c-roles-multiple
	  (map (lambda (roles)
		(let ((c-roles 
		       (map (lambda (r)
			     (let ((r-vec (allocate-ivec (length r))))
			      (for-each-indexed (lambda (x i) (set-ivec! r-vec i x)) r)
			      r-vec))
			    roles)))
		 (list->c-array (malloc (* (length c-roles) c-sizeof-s2cuint))
				c-roles c-s2cuint-set! c-sizeof-s2cuint)))
	       roles-multiple))
	 (c-flow-struct-movie (map (lambda (fl) (optical-flow->c-flow-struct fl)) flow-movie))
	 (c-boxes-movie (map (lambda (boxes-per-frame)
	 		      (map voc4-detection->box boxes-per-frame))
	 		     boxes-movie))
	 (c-boxes-movie-struct (allocate-boxes-movie (length c-boxes-movie)
	 					     (length (first c-boxes-movie))))
	 (_ (for-each-indexed
	     (lambda (boxes-per-frame t)
	      (begin
	       (unless (= (length boxes-per-frame) (* (length *model-groups*) (top-n)))
	 	(panic "sentence-likelihood-one-video-with-computed-boxes: box number error ~a"
	 	       (length boxes-per-frame)))
	       (for-each-indexed
	 	(lambda (box i)
	 	 (set-boxes-movie! c-boxes-movie-struct t i box))
	 	boxes-per-frame)))
	     c-boxes-movie))
	 (likelihoods-array
	  (with-c-pointers
	   (lambda (sentences-array)
	    (with-vector->c-exact-array
	     (lambda (n-tracks-multiple-array)
	      (with-c-pointers
	       (lambda (c-roles-multiple-array)
		(with-c-pointers
		 (lambda (c-flow-struct-array)
		  (with-c-strings
		   (lambda (objects-array)
		    (with-c-pointers
		     (lambda (c-feature-medoids-array)
		      (sentence-likelihoods-one-video
		       sentences-array (length sentences) n-tracks-multiple-array
		       c-roles-multiple-array c-boxes-movie-struct
		       c-flow-struct-array (length flow-movie)  
		       (get-scale video) objects-array (length *objects*)
		       c-feature-medoids-array (length *c-feature-medoids*)
		       model-constraint (if final-state? 1 0)))
		     (list->vector *c-feature-medoids*)))
		   (list->vector *objects*)))
		 (list->vector c-flow-struct-movie)))
	       (list->vector c-roles-multiple)))
	     c-sizeof-int 
	     (list->vector n-tracks-multiple)
	     #t))
	   (list->vector sentences)))
	 (likelihoods (map (lambda (likelihood) (/ likelihood (length boxes-movie)))
			   (c-inexact-array->list likelihoods-array c-sizeof-double (length sentences) #t))))
   (for-each free-sentence sentences)
   (for-each (lambda (c-roles-array roles)
	      (for-each free-ivec
			(c-exact-array->list
			 c-roles-array c-sizeof-s2cuint
			 (length roles) #f))
	      (free c-roles-array))
	     c-roles-multiple roles-multiple)
   (for-each free-c-flow-struct c-flow-struct-movie)
   (for-each (lambda (boxes) (for-each free-box boxes)) c-boxes-movie)
   (free-boxes-movie c-boxes-movie-struct)
   (free likelihoods-array)
   likelihoods)))

(define (sentence-likelihood video plain-sentence model-constraint models
			     write-result? final-state? write-path)
 (let* ((detector-boxes-movies (map (lambda (model-name)
				     (read-voc4-detector-boxes video model-name))
				    (join *model-groups*)))
	(flow-movie (read-optical-flow-movie-in-c video))
	(processed-tracks
	 (map
	  (lambda (model-group)
	   (first
	    (viterbi-prepare-tracks
	     (get-scale video) (video-dimensions video) (video-length video)
	     flow-movie
	     (map (lambda (model-name)
		   (find-if (lambda (x) (equal? model-name (second x)))
			    (zip detector-boxes-movies (join *model-groups*))))
		  model-group)
	     (top-n)
	     (model-path)
	     *model-threshold-tracker-offset*
	     *profile-best-boxes?*
	     (prediction-lookahead))))
	  *model-groups*))
	(boxes-movie (map join (transpose-list-of-lists
				(map (lambda (movie)
				      (map (lambda (boxes)
					    (pad-with-last-if-necessary boxes (top-n))) movie))
				     (reselect-topn-boxes processed-tracks (top-n))))))
	(flow-movie (pad-with-last-if-necessary flow-movie
						(length (first detector-boxes-movies))))
	(models (map (lambda (model)
		      (make-trained-hmm
		       (c-string->symbol (model-name model))
		       '()
		       (model-uu model)
		       0
		       ;; Smooth the roc curve
		       (begin
			(smooth-model! model (if (upper-triangular?) 1 0) 1e-100)
			model)
		       2
		       #f
		       'trained))
		     models))
	(likelihood
	 (first 
	  (sentence-likelihoods-one-video-with-computed-boxes
	   video (list plain-sentence) model-constraint
	   models boxes-movie flow-movie final-state?))))
  (for-each (lambda (flow) (free (c-optical-flow-handle flow)))
	    (but-last flow-movie))
  (format #t "~a ~%" likelihood)
  (when write-result?
   (write-object-to-file (list plain-sentence likelihood) write-path))
  likelihood))
