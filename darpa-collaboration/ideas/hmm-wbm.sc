(MODULE HMM-WBM)
;;; LaHaShem HaAretz U'Mloah

(include "QobiScheme.sch")
(include "hmm-wbm.sch")
(include "toollib-c-macros.sch")

(c-include "hmm-def.h")
(c-include "hmm.h")
(c-include "hmm-data.h")
(c-include "hmm-control.h")
(c-include "hmm-features.h")
(c-include "hmm-likelihood-AD.h")
(c-include "idealib-c.h")

;;; Structures

(define-structure psi name part-of-speech features n-roles parameters a b)

;;; enum part_of_speech
(define pos-c:noun (c-value int "NOUN"))
(define pos-c:verb (c-value int "VERB"))
(define pos-c:adverb (c-value int "ADVERB"))
(define pos-c:adjective (c-value int "ADJECTIVE"))
(define pos-c:preposition (c-value int "PREPOSITION"))
(define pos-c:motion-preposition (c-value int "MOTION_PREPOSITION"))
(define pos-c:other (c-value int "OTHER"))

;; enum one_track_feature_type
(define feature-type-c:flow-magnitude-in-box (c-value int "FLOW_MAGNITUDE_IN_BOX"))
(define feature-type-c:flow-orientation-in-box (c-value int "FLOW_ORIENTATION_IN_BOX"))
(define feature-type-c:displacement (c-value int "DISPLACEMENT"))
(define feature-type-c:position-x (c-value int "POSITION_X"))
(define feature-type-c:area (c-value int "AREA"))
(define feature-type-c:model-name (c-value int "MODEL_NAME"))
(define feature-type-c:model-color (c-value int "MODEL_COLOR"))

(define training-mode-c:ml (c-value int "HMM_ML"))
(define training-mode-c:dt (c-value int "HMM_DT"))
(define training-mode-c:mixed (c-value int "HMM_MIXED"))

(define model-constraint:none (c-value int "NO_MODEL_CONSTRAINT"))
(define model-constraint:no-duplicates (c-value int "NO_DUPLICATE_MODELS"))
;;; Procedures

(define update-results-discrimination
 (c-function double ("update_results_discrimination" pointer)))

(define update-results-likelihood
 (c-function double ("update_results_likelihood" pointer)))

(define allocate-ivec
 (c-function pointer ("allocIVec" int)))

(define set-ivec!
 (c-function void ("setIVec" pointer int int)))

(define free-ivec
 (c-function void ("freeIVec" pointer)))

(define allocate-rmat
 (c-function pointer ("allocRMat" int int)))

(define rmat-get
 (c-function double ("rmat_get" pointer int int)))

(define rmat-set!
 (c-function void ("rmat_set" pointer int int double)))

(define free-rmat
 (c-function void ("freeRMat" pointer)))

(define allocate-rmat-vector
 (c-function pointer ("allocate_rmat_vector" int)))

(define rmat-vector-set!
 (c-function void ("rmat_vector_set" pointer int pointer)))

(define free-rmat-vector
 (c-function void ("free_rmat_vector" pointer)))

(define allocate-model
 (c-function pointer ("allocModel" int int)))

(define copy-model!
 (c-function void ("copyModel" pointer pointer)))

(define (clone-model model)
 (let* ((m (trained-hmm-model model))
	(mm (allocate-model (model-ii m) (model-uu m))))
  (copy-model! mm m)
  (make-trained-hmm (trained-hmm-name model)
		    (trained-hmm-videos model)
		    (trained-hmm-states model)
		    (trained-hmm-log-likelihood model)
		    mm
		    (trained-hmm-participants model)
		    (trained-hmm-feature-type model)
		    (trained-hmm-training-type model))))

(define model-ii
 (c-function int ("model_ii" pointer)))

(define model-nn
 (c-function int ("model_nn" pointer int)))

(define model-uu
 (c-function int ("model_uu" pointer)))

(define model-feature-type
 (c-function int ("model_feature_type" pointer int)))

(define set-model-feature-type-continuous!
 (c-function void ("defineContFeat" pointer int double)))

(define set-model-feature-type-radial!
 (c-function void ("defineRadialFeat" pointer int)))

(define set-model-feature-type-discrete!
 (c-function void ("defineDiscreteFeat" pointer int)))

(define model-parameter
 (c-function double ("model_parameter" pointer int int int)))

(define set-model-parameter!
 (c-function void ("set_model_parameter" pointer int int int double)))

(define model-a
 (c-function double ("model_a" pointer int int)))

(define set-model-a!
 (c-function void ("set_model_a" pointer int int double)))

(define set-model-a-linear!
 (c-function void ("set_model_a_linear" pointer int int double)))

(define model-b
 (c-function double ("model_b" pointer int)))

(define set-model-b!
 (c-function void ("set_model_b" pointer int double)))

(define set-model-b-linear!
 (c-function void ("set_model_b_linear" pointer int double)))

(define model-name
 (c-function string ("model_name" pointer)))

(define model-n-roles
 (c-function int ("model_n_roles" pointer)))

(define model-pos
 (c-function int ("model_pos" pointer)))

(define set-model-name!
 (c-function void ("set_model_name" pointer string)))

(define set-model-n-roles!
 (c-function void ("set_model_n_roles" pointer int)))

(define set-model-pos!
 (c-function void ("set_model_pos" pointer int)))

(define (uniform-model! m upper-triangular?)
 ((c-function void ("uniformModel" pointer int))
  m
  (if upper-triangular? 1 0)))

(define (randomise-model! m upper-triangular?)
 ((c-function void ("randomiseModel" pointer int))
  m
  (if upper-triangular? 1 0)))

(define (noise-model! m upper-triangular? delta)
 ((c-function void ("noiseModel" pointer int double))
  m
  (if upper-triangular? 1 0)
  delta))

(define (smooth-model! m upper-triangular? eps)
 ((c-function void ("smoothModel" pointer int double))
  m
  (if upper-triangular? 1 0)
  eps))

(define print-model
 (c-function void ("print_model" pointer)))

(define free-model
 (c-function void ("freeModel" pointer)))

(define normalize-model!
 (c-function int ("normalizeModel" pointer int int)))

(define zero-model!
 (c-function void ("zeroModel" pointer)))

(define (zero-model? m)
 (if (= ((c-function int ("isZeroModel" pointer)) m) 1)
     #t
     #f))

(define linear->log-model!
 (c-function void ("linear2logModel" pointer)))

(define log->linear-model!
 (c-function void ("log2linearModel" pointer)))

(define model-log-likelihood
 (c-function double ("logLike" pointer pointer)))

(define best-state-sequence
 (c-function pointer ("best_state_sequence" pointer pointer)))

(define force-init-globals!
 (c-function void ("force_init_globals")))

(define allocate-box
 (c-function pointer ("allocBox")))

(define box-coordinates
 (c-function double ("box_coordinates" pointer int)))

(define box-filter
 (c-function int ("box_filter" pointer)))

(define box-strength
 (c-function double ("box_strength" pointer)))

(define box-delta
 (c-function int ("box_delta" pointer)))

(define box-color
 (c-function int ("box_color" pointer)))

(define box-model
 (c-function string ("box_model" pointer)))

(define set-box-coordinates!
 (c-function void ("set_box_coordinates" pointer int double)))

(define set-box-filter!
 (c-function void ("set_box_filter" pointer int)))

(define set-box-strength!
 (c-function void ("set_box_strength" pointer double)))

(define set-box-delta!
 (c-function void ("set_box_delta" pointer int)))

(define set-box-color!
 (c-function void ("set_box_color" pointer int)))

(define set-box-model!
 (c-function void ("set_box_model" pointer string)))

(define free-box
 (c-function void ("freeBox" pointer)))

(define update!
 (c-function int ("update" pointer pointer pointer pointer double
		  pointer int int int int)))

(define allocate-hmm
 (c-function pointer ("allocHMM" int)))

(define free-hmm
 (c-function void ("freeHMM" pointer)))

(define initialize-sentence
 (c-function pointer ("initializeSentence" int pointer)))

(define free-sentence
 (c-function void ("freeSentence" pointer)))

(define (update-model-ml! model scratch-hmm data data-size upper-triangular?)
 (with-c-pointers
  (lambda (model-array)
   (with-c-pointers
    (lambda (scratch-model-array)
     (with-array data-size
		 c-sizeof-int
		 (lambda (class-array)
		  (update!
		   model-array
		   scratch-model-array
		   data
		   0
		   0
		   (vector->c-exact-array class-array (make-vector data-size 0)
					  c-sizeof-int #t)
		   data-size
		   1
		   *hmm-maximum-likelihood-training*
		   (if upper-triangular? 1 0)))))
    (vector scratch-hmm)))
  (vector model)))

(define (update-model-ml-multi! models scratch-hmms data data-size class-assignments upper-triangular?)
 (with-c-pointers
  (lambda (model-array)
   (with-c-pointers
    (lambda (scratch-model-array)
     (with-array data-size
		 c-sizeof-int
		 (lambda (class-array)
		  (update!
		   model-array
		   scratch-model-array
		   data
		   0
		   0
		   (vector->c-exact-array class-array class-assignments c-sizeof-int #t)
		   data-size
		   (vector-length models)
		   *hmm-maximum-likelihood-training*
		   (if upper-triangular? 1 0)))))
    scratch-hmms))
  models))

(define compute-posterior!-internal
 (c-function void ("compute_posterior" pointer pointer pointer
		   pointer int int int pointer
		   pointer pointer)))

(define (compute-posterior! models data priors class-assignments
			    data-size training-mode posterior-array)
 (with-c-pointers
  (lambda (model-array)
   (with-array
    data-size c-sizeof-int
    (lambda (class-array)
     (with-array
      data-size c-sizeof-double
      (lambda (priors-array)
       (with-alloc
	c-sizeof-double
	(lambda (objective-function-value)
	 (with-alloc
	  c-sizeof-double
	  (lambda (auxiliary)
	   (compute-posterior!-internal
	    model-array
	    data
	    (vector->c-inexact-array priors-array priors c-sizeof-double #t)
	    (vector->c-exact-array class-array class-assignments c-sizeof-int #t)
	    data-size
	    (vector-length models)
	    training-mode
	    objective-function-value
	    auxiliary
	    posterior-array)
	   (vector (c-double-ref objective-function-value 0)
		   (c-double-ref auxiliary 0)))))))))))
  models))

(define (update-model-dt! models scratch-hmms data posterior log-D
			  class-assignments data-size upper-triangular?)
 (unless (= (vector-length models) (vector-length scratch-hmms))
  (fuck-up))
 (with-c-pointers
  (lambda (model-array)
   (with-c-pointers
    (lambda (scratch-model-array)
     (with-array data-size
		 c-sizeof-int
		 (lambda (class-array)
		  (update!
		   model-array
		   scratch-model-array
		   data
		   posterior
		   log-D
		   (vector->c-exact-array class-array class-assignments c-sizeof-int #t)
		   data-size
		   (vector-length models)
		   *hmm-discriminative-training*
		   (if upper-triangular? 1 0)))))
    scratch-hmms))
  models))

(define state-probabilities-internal
 (c-function pointer ("state_probabilities" pointer pointer)))

(define (debug-state-probabilities model features number-of-states)
  (let* ((rmat (features->rmat features))
	(array (state-probabilities-internal model rmat)))
	array))

(define (state-probabilities model features number-of-states)
 (let* ((rmat (features->rmat features))
	(array (state-probabilities-internal model rmat))
	(pointers (c-exact-array->list array
					   c-sizeof-long
					   (length features)
					   #f))
	(probabilities (map (lambda (p)
			     (map exp (c-inexact-array->list p
						    c-sizeof-double
						    number-of-states
						    #t)))
			    pointers)))
  (map (lambda (p)
	(free p))
       pointers)
  (free array)
  (free-rmat rmat)
  probabilities))

(define state-probabilities-with-box-scores-internal
 (c-function pointer ("state_probabilities_with_box_scores" pointer
		      pointer pointer)))

(define (state-probabilities-with-box-scores
	 model features number-of-states scores-rmat)
 (let* ((rmat (features->rmat features))
	(array (state-probabilities-with-box-scores-internal
		model rmat scores-rmat))
	(pointers (c-exact-array->list array
				       c-sizeof-long
				       (length features)
				       #f))
	(probabilities (map (lambda (p)
			     (map exp (c-inexact-array->list p
							     c-sizeof-double
							     number-of-states
							     #t)))
			    pointers)))
  (map (lambda (p)
	(free p))
       pointers)
  (free array)
  (free-rmat rmat)
  probabilities))

(define update-with-box-scores!
 (c-function int ("update_with_box_scores" pointer pointer pointer
		  pointer double pointer int int int
		  int pointer)))

(define (update-model-ml-with-box-scores! model scratch-hmm data data-size upper-triangular? scores-rmats)
 (with-c-pointers
  (lambda (model-array)
   (with-c-pointers
    (lambda (scratch-model-array)
     (with-array data-size
		 c-sizeof-int
		 (lambda (class-array)
		  (update-with-box-scores!
		   model-array
		   scratch-model-array
		   data
		   0
		   0
		   (vector->c-exact-array class-array (make-vector data-size 0)
					  c-sizeof-int #t)
		   data-size
		   1
		   *hmm-maximum-likelihood-training*
		   (if upper-triangular? 1 0)
		   scores-rmats))))
    (vector scratch-hmm)))
  (vector model)))

(define model-log-likelihood-with-box-scores
 (c-function double ("logLike_with_box_scores" pointer pointer pointer)))

(define update-x!
 (c-function pointer ("updateX" pointer pointer pointer int pointer pointer pointer
		      int double double int pointer int pointer int int)))

(define sentence-likelihoods-one-video
 (c-function pointer ("sentence_likelihoods_one_video" pointer int pointer pointer pointer pointer
		      int double pointer int pointer int int int)))

(define sentence-maximum-one
 (c-function double ("sentence_maximum_one" pointer int pointer pointer pointer int double
		     pointer int pointer int int int pointer)))

(define sentence-derivatives-one-video
 (c-function pointer ("sentence_derivatives_one_video" pointer int int pointer pointer
		      pointer pointer pointer pointer int double pointer pointer pointer
		      pointer pointer int pointer int int)))

(define model-hmm
 (c-function pointer ("model_hmm" pointer)))

(define model-ffm
 (c-function pointer ("model_ffm" pointer int)))

(define allocate-pack-info
 (c-function pointer ("allocPackInfo" int)))

(define set-pack-info-uu!
 (c-function void ("set_packinfo_uu" pointer int int)))

(define set-pack-info-ii!
 (c-function void ("set_packinfo_ii" pointer int int)))

(define set-pack-info-nn!
 (c-function void ("set_packinfo_nn" pointer int int int)))

(define free-pack-info
 (c-function void ("freePackInfo" pointer)))

;;; Tam V'Nishlam Shevah L'El Borei Olam
