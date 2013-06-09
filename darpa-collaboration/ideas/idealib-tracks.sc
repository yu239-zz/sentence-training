(MODULE IDEALIB-TRACKS)

;;; LaHaShem HaAretz U'Mloah
;;; Copyright 2012 Purdue University. All rights reserved.

(include "QobiScheme-AD.sch")
(include "idealib-tracks.sch")
(include "toollib-c-macros.sch")

(c-include "hmm-def.h")
(c-include "hmm-features.h")
(c-include "idealib-c.h")

;; New strategy, going to name boxes and store a hash table, build a
;; hash out of the list of inputs

;;; Voc4

(define (nondropped-box? box)
 (not (= (voc4-detection-strength box) minus-infinity)))

(define (dropped-box? box)
 (= (voc4-detection-strength box) minus-infinity))

(define (number->string-c-infinity number)
 (cond ((not number) "0")
       ((= number infinity) "infinity")
       ((= number (- infinity)) "-infinity")
       (else (number->string number))))

(define (voc4-box->string box)
 (if (nondropped-box? box)
     (format #f "~s ~s ~s ~s ~a ~s ~a ~s ~a ~a"
	     (voc4-detection-x1 box)
	     (voc4-detection-y1 box)
	     (voc4-detection-x2 box)
	     (voc4-detection-y2 box)
	     (string-join " "
			  (map (lambda (l) (string-join " " (map number->string l)))
			       (voc4-detection-parts box)))
	     (voc4-detection-filter box)
	     (number->string-c-infinity (voc4-detection-strength box))
	     (voc4-detection-delta box)
	     (number->string-c-infinity (voc4-detection-color box))
	     (if (voc4-detection-model box)
		 (voc4-detection-model box)
		 "padding"))
     (format #f "~s ~s ~s ~s ~s ~a ~s ~a ~a"
	     -1 -1 -1 -1
	     (voc4-detection-filter box)
	     (number->string-c-infinity (voc4-detection-strength box))
	     (voc4-detection-delta box)
	     (number->string-c-infinity minus-infinity)
	     (if (voc4-detection-model box)
		 (voc4-detection-model box)
		 "padding"))))

(define (pad-and-smooth-box-movie box-movie frame-ratio x-ratio y-ratio)
 (unless (> frame-ratio 1) (panic "Cannot yet go from higher fps to lower fps"))
 (define (voc4-interpolate b1 b2 n)
  (define (interp v1 v2 i n) (+ v1 (* (/ (- v2 v1) n) i)))
  (map-n
   (lambda (i)
    (make-voc4-detection (interp (voc4-detection-x1 b1) (voc4-detection-x1 b2) i n)
			 (interp (voc4-detection-y1 b1) (voc4-detection-y1 b2) i n)
			 (interp (voc4-detection-x2 b1) (voc4-detection-x2 b2) i n)
			 (interp (voc4-detection-y2 b1) (voc4-detection-y2 b2) i n)
			 (map (lambda (p1 p2) (map (lambda (c1 c2) (interp c1 c2 i n)) p1 p2))
			      (voc4-detection-parts b1)
			      (voc4-detection-parts b2))
			 (voc4-detection-filter b1)
			 (voc4-detection-strength b1)
			 (voc4-detection-delta b1)
			 (voc4-detection-color b1)
			 (voc4-detection-model b1)))
   n))
 (define (voc4-rescale b x-ratio y-ratio)
  (make-voc4-detection (* (voc4-detection-x1 b) x-ratio)
		       (* (voc4-detection-y1 b) y-ratio)
		       (* (voc4-detection-x2 b) x-ratio)
		       (* (voc4-detection-y2 b) y-ratio)
		       (map (lambda (p) (map * p (list x-ratio y-ratio x-ratio y-ratio)))
			    (voc4-detection-parts b))
		       (voc4-detection-filter b)
		       (voc4-detection-strength b)
		       (voc4-detection-delta b)
		       (voc4-detection-color b)
		       (voc4-detection-model b)))
 (let* ((bm (map (lambda (b) (voc4-rescale b x-ratio y-ratio)) box-movie))
	(num (exact-round frame-ratio))
	(new-box-movie
	 (let loop ((b1 (car bm)) (r (rest bm)) (nbm '()))
	  (if (null? r)
	      ;; find and kill all *#&**!* ffmpeg developers
	      ;; sometimes #frames = #boxes + 1, so I need to pad the last frame twice
	      ;; just in case.
	      (join (reverse (cons (map-n (lambda _ b1) (+ num num)) nbm)))
	      (loop (car r) (cdr r) (cons (voc4-interpolate b1 (car r) num) nbm)))))
	(l (length (remove-if-not nondropped-box? new-box-movie)))
	(pieces (max 5 (exact-round (* (/ l 140) 10)))))
  (smooth-box-movie new-box-movie pieces pieces pieces pieces)))

(define (crop-track-from-video video-name model number destination)
 (crop-box-movie-from-video
  video-name
  (read-voc4-tracked-boxes video-name model number)
  destination))

(define (crop-box-movie-from-video video-name box-movie destination)
 (with-ffmpeg-video
  video-name
  (lambda (ffmpeg-video)
   (for-each-indexed
    (lambda (box frame-number)
     (write-pnm
      (crop-voc4 (ffmpeg-video-frame-data ffmpeg-video)
		 (exact-round-voc4-detection box))
      (string-append destination
		     (number->padded-string-of-length
		      (+ frame-number (video-first-frame video-name))
		      6)
		     ".ppm"))
     (ffmpeg-next-frame! ffmpeg-video))
    box-movie))))

(define (consolidate-intervals intervals interval)
 ;; This assumes the intervals are disjoint.
 ;; If you break this, I *will* find you, and you will find the 7
 ;; hounds of hell will seem as kittens compared to me armed with a
 ;; pencil.
 (define (subinterval? a b) (and (>= (car a) (car b)) (<= (cadr a) (cadr b))))
 (define (in-interval? a i) (<= (car i) a (cadr i)))
 (cond ((some (lambda (i) (subinterval? interval i)) intervals) intervals)
       ((some (lambda (i) (subinterval? i interval)) intervals)
	(list-replace intervals
		      (find-if (lambda (i) (subinterval? i interval)) intervals)
		      interval))
       ((or (some (lambda (i) (in-interval? (car interval) i)) intervals)
	   (some (lambda (i) (in-interval? (cadr interval) i)) intervals))
	#f)
       (else (sort (cons interval intervals) < car))))

(define (subinterval? a b) (and (>= (car a) (car b)) (<= (cadr a) (cadr b))))
(define (in-interval? a i) (<= (car i) a (cadr i)))
(define (interval-overlaps? a b)
 (or (in-interval? (car a) b) (in-interval? (cadr a) b)
    (subinterval? a b) (subinterval? b a)))

(define (intervals->strings intervals)
 (map (lambda (i) (string-join " " (map number->string i))) intervals))

(define (sort-intervals intervals) (sort intervals < car))

(define (with-interval-based-zip-file f get-in-zip-filename zip-filename video-name)
 (let* ((intervals-file (intervals-in-zip-pathname))
        (intervals (and (file-exists? zip-filename)
		      (zip:get-intervals-from-file zip-filename)))
        (interval (video-interval video-name))
        (overlaps
         (and intervals
	    (sort-intervals
	     (remove-if-not (lambda (i) (interval-overlaps? i interval)) intervals))))
        (new-intervals
         (sort-intervals (cons interval (if (and intervals overlaps)
					    (set-difference intervals overlaps)
					    (if intervals intervals '()))))))
  (format #t "intervals: ~a" intervals) (newline)
  (format #t "interval: ~a" interval) (newline)
  (format #t "overlap: ~a" overlaps) (newline)
  (when overlaps
   (with-zip-file
    (lambda (zip)
     (let ((zip-contents (zip:ls zip)))
      (for-each
       (lambda (overlapping-interval)
	(for-each-frame-reversed
	 (lambda (frame)
	  (let ((dirname (string-append (number->padded-string-of-length frame 6) "/")))
	   ;; (zip:delete zip dirname)
	   (write (get-in-zip-filename frame)) (newline)
	   (when (member (get-in-zip-filename frame) zip-contents)
	    (zip:delete zip (get-in-zip-filename frame)))))
	 (let ((v (map-vector identity video-name)))
	  (write overlapping-interval)
	  (set-video-start! v (car overlapping-interval))
	  (set-video-end! v (cadr overlapping-interval))
	  v)))
       (reverse overlaps))))
    zip-filename
    *zip:mode-open*))
  (unless intervals (rm-if-necessary zip-filename))
  (with-zip-file
   (lambda (zip)
    (when intervals (zip:delete zip intervals-file))
    (zip:add-file zip intervals-file (intervals->strings new-intervals))
    (f zip))
   zip-filename
   *zip:mode-create-if-necessary*)))

(define (write-boxes-movie boxes-movie video-name type label num ext)
 ;; TODO This is a hack for now, need better handling of errors in
 ;; zip:* functions
 (with-interval-based-zip-file
  (lambda (zip)
   (for-each-frame-indexed
    (lambda (frame offset)
     ;; (zip:add-directory zip (number->padded-string-of-length frame 6))
     (zip:add-file zip
                   (per-frame-boxes-in-zip-pathname frame type label num ext)
                   (map voc4-box->string (list-ref boxes-movie offset))))
    video-name))
  (lambda (frame) (per-frame-boxes-in-zip-pathname frame type label num ext))
  (per-video-box-pathname video-name type label num ext)
  video-name))

(define (write-box-movie box-movie video-name type label num ext)
 (write-boxes-movie (map list box-movie) video-name type label num ext))

(define (write-voc4-boxes-movie boxes-movie video-name model)
 (write-boxes-movie boxes-movie video-name "voc4" model #f "boxes"))

(define (write-voc4-irobot-boxes-movie boxes-movie video-name model)
 (write-boxes-movie boxes-movie video-name "voc4_irobot" model #f "boxes"))

(define (write-voc4-predicted-boxes-movie boxes-movie video-name model)
 (write-boxes-movie boxes-movie video-name "voc4" model #f "predicted-boxes"))

(define (write-voc4-tracked-box-movie box-movie video-name model number)
 (write-box-movie box-movie video-name "voc4" model number "tracked-box"))

(define (write-voc4-smooth-tracked-box-movie box-movie video-name model number)
 (write-box-movie
  box-movie video-name "voc4" model number "smooth-tracked-box"))

(define (write-voc4-overgenerated-boxes-movie boxes-movie video-name model)
 (write-boxes-movie
  boxes-movie video-name "voc4_overgenerated" model #f "boxes"))

(define (write-voc4-overgenerated-predicted-boxes-movie boxes-movie
							video-name model)
 (write-boxes-movie
  boxes-movie video-name "voc4_overgenerated" model #f "predicted-boxes"))

(define (write-voc4-overgenerated-tracked-box-movie box-movie video-name
						    model number)
 (write-box-movie
  box-movie video-name "voc4_overgenerated" model number "tracked-box"))

(define (write-voc4-overgenerated-smooth-tracked-box-movie box-movie video-name
							   model number)
 (write-box-movie
  box-movie video-name "voc4_overgenerated" model number "smooth-tracked-box"))

(define (write-voc4-event-boxes-movie boxes-movie video-name model)
 (write-boxes-movie boxes-movie video-name "voc4_event" model #f "boxes"))

(define (write-voc4-event-predicted-boxes-movie boxes-movie video-name model)
 (write-boxes-movie
  boxes-movie video-name "voc4_event" model #f "predicted-boxes"))

(define (write-voc4-event-tracked-box-movie box-movie video-name model number)
 (write-box-movie
  box-movie video-name "voc4_event" model number "tracked-box"))

(define (write-voc4-event-smooth-tracked-box-movie box-movie video-name
						   model number)
 (write-box-movie
  box-movie video-name "voc4_event" model number "smooth-tracked-box"))

(define (write-voc4-sentence-tracked-box-movie box-movie video-name model number)
 (write-box-movie box-movie video-name "voc4_sentence" model number "tracked-box"))

(define (write-voc4-sentence-smooth-tracked-box-movie box-movie video-name
						      model number)
 (write-box-movie box-movie video-name "voc4_sentence" model number "smooth-tracked-box"))

(define (quantize-voc4-detection detection)
 (make-voc4-detection
  (quantize-coordinate (voc4-detection-x1 detection))
  (quantize-coordinate (voc4-detection-y1 detection))
  (quantize-coordinate (voc4-detection-x2 detection))
  (quantize-coordinate (voc4-detection-y2 detection))
  (voc4-detection-parts detection)
  (voc4-detection-filter detection)
  (voc4-detection-strength detection)
  (voc4-detection-delta detection)
  (voc4-detection-color detection)
  (voc4-detection-model detection)))

(define (unpack-consolidated-frames data)
 (let loop ((data data) (out '()))
  (cond ((null? data) (reverse out))
	((equal? "" (first data)) (loop (cdr data) out))
	(else (loop (drop (string->number (first data)) (cdr data))
		    (cons (take (string->number (first data)) (cdr data))
			  out))))))

(define (vector-string->voc4-inexact-box e string . default-model)
 (make-voc4-detection
  (vector-ref e 0) (vector-ref e 1)
  (vector-ref e 2) (vector-ref e 3)
  (let loop ((i 4))
   (if (<= (- (vector-length e) i) 4)
       '()
       (cons (list (vector-ref e i) (vector-ref e (+ i 1))
		   (vector-ref e (+ i 2)) (vector-ref e (+ i 3)))
	     (loop (+ i 4)))))
  (exact-round
   (let ((a (vector-ref e (- (vector-length e) 4))))
    (if (or (= a infinity) (= a minus-infinity)) -1 a)))
  (vector-ref e (- (vector-length e) 3))
  (exact-round
   (let ((a (vector-ref e (- (vector-length e) 2))))
    (if (or (= a infinity) (= a minus-infinity)) -1 a)))
  (vector-ref e (- (vector-length e) 1))
  (if (equal? "" string)
      (if (null? default-model)
	  (fuck-up)
	  (first default-model))
      (field-ref string 0))))

(define (exact-round-voc4-detection box)
 (make-voc4-detection
  (exact-round (voc4-detection-x1 box))
  (exact-round (voc4-detection-y1 box))
  (exact-round (voc4-detection-x2 box))
  (exact-round (voc4-detection-y2 box))
  (map (lambda (parts) (map exact-round parts))
       (voc4-detection-parts box))
  (voc4-detection-filter box)
  (voc4-detection-strength box)
  (voc4-detection-delta box)
  (voc4-detection-color box)
  (voc4-detection-model box)))

(define (fast-read-boxes-from-zip zip filename)
 (let* ((buffer (zip:read-file-to-buffer zip filename)))
  (fast-read-boxes-from-buffer (x buffer) "")))

(define (padding-detections)
 (list
  (make-voc4-detection -1 -1 -1 -1 '() -1 minus-infinity 0 minus-infinity #f)))

(define (zip:get-intervals zip)
 (and (member (intervals-in-zip-pathname) (zip:ls zip))
    (map (lambda (i) (map string->number (fields i)))
	 (zip:read-file zip (intervals-in-zip-pathname)))))

(define (zip:get-intervals-from-file filename)
 (with-zip-file (lambda (zip) (zip:get-intervals zip)) filename *zip:mode-open*))

(define (zip:assert-good-intervals! zip video-name)
 (let ((intervals (zip:get-intervals zip))
       (interval (video-interval video-name)))
  (unless (or (not intervals) (some (lambda (i) (subinterval? interval i)) intervals))
   (panic "Interval ~a can't be read from file with intervals ~a"
          interval intervals))))

(define (fast-read-zip-boxes video-name type label num ext expect-last?)
 (with-zip-file
  (lambda (zip)
   (zip:assert-good-intervals! zip video-name)
   ((if expect-last? identity (lambda (a) (append a (list (padding-detections)))))
    ((if expect-last? map-frame map-frame-but-last)
     (lambda (frame)
      (let ((boxes
	     (fast-read-boxes-from-zip
	      zip (per-frame-boxes-in-zip-pathname frame type label num ext))))
       ;; TODO this should be cleaned up when fast-read-boxes-* loses
       ;; the ability to read per-video files
       ;;;;;; Deal with model-name as '.' Should be removed in the future ;;;;;
       (when (> (length boxes) 1)
	(panic "Boxes file with ~a frames in a per-frame file at frame ~a"
	       (length boxes) frame))
       (if (null? boxes) '() (first boxes))))
     video-name)))
  (per-video-box-pathname video-name type label num ext)
  *zip:mode-open*))

(define (read-detector-boxes video-name type model)
 (fast-read-zip-boxes video-name type model #f "boxes" #t))

(define (read-predicted-boxes video-name type model)
 (fast-read-zip-boxes video-name type model #f "predicted-boxes" #t))

(define (read-tracked-boxes video-name type model number)
 (map first (fast-read-zip-boxes
	     video-name type model number "tracked-box" #t)))

(define (read-smooth-tracked-boxes video-name type model number)
 (map first (fast-read-zip-boxes
	     video-name type model number "smooth-tracked-box" #t)))

(define (read-voc4-detector-boxes video-name model)
 (read-detector-boxes video-name "voc4" model))

(define (read-voc4-predicted-boxes video-name model)
 (read-predicted-boxes video-name "voc4" model))

(define (read-voc4-smooth-tracked-boxes video-name model number)
 (read-smooth-tracked-boxes video-name "voc4" model number))

(define (read-voc4-tracked-boxes video-name model number)
 (read-tracked-boxes video-name "voc4" model number))

(define (read-voc4-overgenerated-detector-boxes video-name model)
 (read-detector-boxes video-name "voc4_overgenerated" model))

(define (read-voc4-overgenerated-predicted-boxes video-name model)
 (read-predicted-boxes video-name "voc4_overgenerated" model))

(define (read-voc4-overgenerated-smooth-tracked-boxes video-name model number)
 (read-smooth-tracked-boxes video-name "voc4_overgenerated" model number))

(define (read-voc4-overgenerated-tracked-boxes video-name model number)
 (read-tracked-boxes video-name "voc4_overgenerated" model number))

(define (read-voc4-event-detector-boxes video-name model)
 (read-detector-boxes video-name "voc4_event" model))

(define (read-voc4-event-predicted-boxes video-name model)
 (read-predicted-boxes video-name "voc4_event" model))

(define (read-voc4-event-smooth-tracked-boxes video-name model number)
 (read-smooth-tracked-boxes video-name "voc4_event" model number))

(define (read-voc4-event-tracked-boxes video-name model number)
 (read-tracked-boxes video-name "voc4_event" model number))

(define (read-voc4-sentence-smooth-tracked-boxes video-name model number)
 (read-smooth-tracked-boxes video-name "voc4_sentence" model number))

(define (read-voc4-sentence-tracked-boxes video-name model number)
 (read-tracked-boxes video-name "voc4_sentence" model number))

(define (crop-voc4 image voc4)
 (crop-image image
	     (voc4-detection-x1 voc4)
	     (voc4-detection-y1 voc4)
	     (min (voc4-detection-width voc4)
		  (- (pnm-width image)
		     (voc4-detection-x1 voc4)
		     1))
	     (min (voc4-detection-height voc4)
		  (- (pnm-height image)
		     (voc4-detection-y1 voc4)
		     1))))

(define (voc4-scale voc4 scale)
 ;; TODO Image boundaries
 (let ((w (voc4-detection-width voc4))
       (h (voc4-detection-height voc4))
       (xc (/ (+ (voc4-detection-x2 voc4) (voc4-detection-x1 voc4)) 2))
       (yc (/ (+ (voc4-detection-y2 voc4) (voc4-detection-y1 voc4)) 2)))
  (make-voc4-detection
   (quantize-coordinate (- xc (/ (* w scale) 2)))
   (quantize-coordinate (- yc (/ (* h scale) 2)))
   (quantize-coordinate (+ xc (/ (* w scale) 2)))
   (quantize-coordinate (+ yc (/ (* h scale) 2)))
   '()
   (voc4-detection-filter voc4)
   (voc4-detection-strength voc4)
   (voc4-detection-delta voc4)
   (voc4-detection-color voc4)
   (voc4-detection-model voc4))))

(define (voc4-bloat voc4 p) (voc4-scale voc4 (+ 1 p)))

(define (voc4-shrink voc4 p) (voc4-scale voc4 (- 1 p)))

(define (voc4->bb voc4)
 (vector (voc4-detection-x1 voc4) (voc4-detection-y1 voc4)
	 (voc4-detection-x2 voc4) (voc4-detection-y2 voc4)))

(define (video-boxes-available video type kind)
 (map
  (lambda (pathname)
   (let* ((parts (pregexp-split "-" (strip-extension
				     (strip-extension
				      (strip-directory pathname)))))
	  (reverse-parts (reverse parts)))
    (list (first parts)
	  (if (string->number (first  reverse-parts))
	      (string-join "-" (cdr (reverse (cdr reverse-parts))))
	      (string-join "-" (cdr parts)))
	  (if (string->number (first  reverse-parts))
	      (last parts)
	      #f)
	  (extension (strip-extension pathname)))))
  (directory-list (generic-root-pathname
		   video (string-append "/" type "-*." kind ".zip")))))

(define (video-detector-boxes-available video type)
 (video-boxes-available video type "boxes"))

(define (video-predicted-boxes-available video type)
 (video-boxes-available video type "predicted-boxes"))

(define (video-tracked-boxes-available video type)
 (video-boxes-available video type "tracked-box"))

(define (video-smooth-tracked-boxes-available video type)
 (video-boxes-available video type "smooth-tracked-box"))

(define (video-voc4-detector-boxes-available video)
 (video-boxes-available video "voc4" "boxes"))

(define (video-voc4-predicted-boxes-available video)
 (video-boxes-available video "voc4" "predicted-boxes"))

(define (video-voc4-tracked-boxes-available video)
 (video-boxes-available video "voc4" "tracked-box"))

(define (video-voc4-smooth-tracked-boxes-available video)
 (video-boxes-available video "voc4" "smooth-tracked-box"))

(define (video-voc4-sentence-tracked-boxes-available video)
 (video-boxes-available video "voc4_sentence" "tracked-box"))

(define (video-voc4-sentence-smooth-tracked-boxes-available video)
 (video-boxes-available video "voc4_sentence" "smooth-tracked-box"))

(define (video-voc4-overgenerated-tracked-boxes-available video)
 (video-boxes-available video "voc4_overgenerated" "tracked-box"))

(define (video-voc4-overgenerated-smooth-tracked-boxes-available video)
 (video-boxes-available video "voc4_overgenerated" "smooth-tracked-box"))

(define (video-voc4-human-smooth-tracked-boxes-available video)
 (video-boxes-available video "voc4_human" "smooth-tracked-box"))

;;; (map-with-lookahead list 2 '(1 2 3 4) '(a b c d))
;;;     ==> (((1 2) (A B)) ((2 3) (B C)) ((3 4) (C D)) ((4) (D)))
(define (map-with-lookahead f n . lists)
 (vector->list
  (apply map-vector-with-lookahead (cons f (cons n (map list->vector lists))))))

;;; (map-vector-with-lookahead list 3 '#(a b c d) '#(1 2 3 4))
;;;     ==> #(((A B C) (1 2 3)) ((B C D) (2 3 4)) ((C D) (3 4)) ((D) (4)))
(define (map-vector-with-lookahead f n . vectors)
 (let* ((len (vector-length (first vectors)))
	(window-fun (lambda (v start-index)
		     (map
		      (lambda (a) (vector-ref v a))
		      (remove-if
		       (lambda (a) (> a (- len 1)))
		       (map  (lambda (a) (+ start-index a)) (enumerate n)))))))
  (list->vector
   (map-n
    (lambda (start-index)
     (apply f (map (lambda (v) (window-fun v start-index)) vectors)))
    len))))

(define (vector-orientation v) (atan (y v) (x v)))

(define (voc4-detection-width detection)
 (- (voc4-detection-x2 detection) (voc4-detection-x1 detection)))

(define (voc4-detection-height detection)
 (- (voc4-detection-y2 detection) (voc4-detection-y1 detection)))

(define (voc4-detection-center detection)
 (vector (/ (+ (voc4-detection-x2 detection) (voc4-detection-x1 detection)) 2)
	 (/ (+ (voc4-detection-y2 detection) (voc4-detection-y1 detection)) 2)))

(define (voc4-detection-aspect-ratio detection)
 (if (zero? (voc4-detection-height detection))
     (begin
      (unless (and (= -1 (voc4-detection-x2 detection))
		 (= -1 (voc4-detection-y2 detection)))
       (format #t "Box with zero size and it's not a dummy!~%"))
      0)
     (/ (voc4-detection-width detection) (voc4-detection-height detection))))

(define (voc4-detection-area detection)
 (let ((x1 (voc4-detection-x1 detection))
       (x2 (voc4-detection-x2 detection))
       (y1 (voc4-detection-y1 detection))
       (y2 (voc4-detection-y2 detection)))
  (* (- x2 x1) (- y2 y1))))

(define (voc4-detection-intersection-area box1 box2)
 ;; Intersection area of two voc4 boxes
 (let ((x1 (max (voc4-detection-x1 box1) (voc4-detection-x1 box2)))
       (x2 (min (voc4-detection-x2 box1) (voc4-detection-x2 box2)))
       (y1 (max (voc4-detection-y1 box1) (voc4-detection-y1 box2)))
       (y2 (min (voc4-detection-y2 box1) (voc4-detection-y2 box2))))
  (cond
   ((or (> 0 (voc4-detection-area box1)) (> 0 (voc4-detection-area box2))) -1)
   ((or (<= x2 x1) (<= y2 y1)) 0)
   (else (* (- x2 x1) (- y2 y1))))))

(define (voc4-detection-union-area box1 box2)
 ;; Union area of two voc4 boxes
 (let ((box1-area (voc4-detection-area box1))
       (box2-area (voc4-detection-area box2))
       (intersection-area (voc4-detection-intersection-area box1 box2)))
  (if (and (> box1-area 0) (> box2-area 0))
      (- (+ box1-area box2-area) intersection-area)
      0)))

(define (voc4-detection-intersection-divided-by-union box1 box2)
 ;; Intersection-area/union-area for voc4 boxes
 (let ((union-area (voc4-detection-union-area box1 box2))
       (intersection-area (voc4-detection-intersection-area box1 box2)))
  (if (> union-area 0)
      (/ intersection-area union-area)
      0)))

(define (non-maximal-suppression voc4-boxes nms-threshold)
 ;; Turn off suppression by passing nms-threshold = 1
 (define (area-overlap box1 box2)
  (/ (voc4-detection-intersection-area box1 box2) (voc4-detection-area box2)))
 (let loop ((boxes (sort voc4-boxes > voc4-detection-strength))
	    (good-boxes '()))
  (if (null? boxes)
      (reverse good-boxes)
      (loop (remove-if (lambda (test-box)
			(> (area-overlap (first boxes) test-box) nms-threshold))
		       (rest boxes))
	    (cons (first boxes) good-boxes)))))

(define (top-n-non-maximal-suppression voc4-boxes nms-threshold top-n)
 ;; Turn off suppression by passing nms-threshold = 1
 ;; Asymptotic complexity is top-n * (length voc4-boxes)
 ;; Results are always sorted
 (define (apply-box test-box boxes)
  (define (area-overlap box1 box2)
   (/ (voc4-detection-intersection-area box1 box2) (voc4-detection-area box2)))
  (define (test-box-stronger-than box)
   (> (voc4-detection-strength test-box) (voc4-detection-strength box)))
  (define (test-box-overlaps-with box)
   (> (area-overlap box test-box) nms-threshold))
  (let* ((weaker-boxes (remove-if-not test-box-stronger-than boxes))
	 (stronger-boxes (remove-if test-box-stronger-than boxes))
	 (result (append (remove-if test-box-overlaps-with weaker-boxes)
			 (if (some test-box-overlaps-with stronger-boxes)
			     '() ; A stronger box suppresses test-box
			     (list test-box))
			 stronger-boxes)))
   ;; We keep (length result) no larger than top-n
   (if (> (length result) top-n)
       (rest result) ; We never add more than 1 box
       result)))
 (let loop ((boxes (sort voc4-boxes > voc4-detection-strength))
	    (good-boxes '()))
  (if (null? boxes)
      (reverse good-boxes) ;; Note that apply-box is always sorted weak=>strong
      (loop (rest boxes) (apply-box (first boxes) good-boxes)))))

(define (voc4-detection->corners box)
 ;; clockwise tl, tr, br, bl
 (list (vector (voc4-detection-x1 box) (voc4-detection-y1 box))
       (vector (voc4-detection-x2 box) (voc4-detection-y1 box))
       (vector (voc4-detection-x2 box) (voc4-detection-y2 box))
       (vector (voc4-detection-x1 box) (voc4-detection-y2 box))))

(define (point-in-voc4-detection? point box)
 (and (<= (voc4-detection-x1 box) (x point) (voc4-detection-x2 box))
    (<= (voc4-detection-y1 box) (y point) (voc4-detection-y2 box))))

;;; siddharth: hack - ideally, this should be propogated through the call chains
(define *finite-difference-scale* 1)
(define (feature-finite-difference feature lookahead)
 (map
  (lambda (v)
   (cond ((list? v) (map (lambda (e) (* e *finite-difference-scale*)) v))
	 ((vector? v) (k*v *finite-difference-scale* v))
	 ((number? v) (* *finite-difference-scale* v))
	 (else (panic "feature-finite-difference: Unsupported form for v"))))
  (let* ((l (cdr
	     (reverse
	      (map-with-lookahead
	       (lambda (i)
		(if (null? (cdr i))
		    #f
		    (list-mean (map (lambda (a b) ((if (vector? a) v- -) b a))
				    (but-last i) (cdr i)))))
	       lookahead
	       feature)))))
   (reverse (cons (car l) l)))))

;;; Returns the areas of the boxes, all divided by the mean area of all the
;;; boxes, after accounting for 'invalid-track' boxes with area -1.
(define (normalized-area boxes)
 (let* ((raw-area (map voc4-detection-area boxes))
	(valid-areas (remove-if (lambda (n) (< n 0)) raw-area))
	(mean-area (if (null? valid-areas) '() (list-mean valid-areas))))
  (map (lambda (a) (if (< a 0) -1 (/ a mean-area))) raw-area)))

(define (person-boxes? boxes)
 (prefix? "person" (most-frequent-class boxes)))

(define (normalize-line-in-voc4-box l b)
 (let* ((vec (v- (q l) (p l)))
	(aspect-ratio (abs (voc4-detection-aspect-ratio b)))
	(new-ht (sqrt (/ aspect-ratio)))
	(new-wd (* aspect-ratio new-ht)))
  (vector (magnitude (vector (* (x vec) (/ new-wd (voc4-detection-width b)))
			     (* (y vec) (/ new-ht (voc4-detection-height b)))))
	  (vector-orientation vec))))

(define (part-center l)
 (vector (/ (+ (first l) (third l)) 2)
	 (/ (+ (second l) (fourth l)) 2)))

(define (part-boxes->part-features box empty-parts)
 (if (null? (voc4-detection-parts box))
     ;; This happens to boxes which have padding
     (map-vector (lambda (r) (if (every (lambda (e) (= e -1)) (vector->list r))
			    '#(-1 -1) ; the feature displacement for the part boxes
			    (fuck-up) ; it should never be triggered
			    ))
		 empty-parts)
     (map-vector
      (lambda (part)
       (normalize-line-in-voc4-box
	(make-line-segment (voc4-detection-center box) (part-center part))
	box))
      (list->vector (voc4-detection-parts box)))))

(define (get-part-vectors boxes)
 (let ((box
	(find-if (lambda (box) (not (null? (voc4-detection-parts box)))) boxes)))
  (unless box (panic "Track contains only padding"))
  (let ((empty-parts
	 (map-matrix
	  (const -1)
	  (list->vector (map list->vector (voc4-detection-parts box))))))
   (map
    (lambda (box) (vector->list
	      (unshape-matrix (part-boxes->part-features box empty-parts))))
    boxes))))

(define (get-changes-in-part-vectors part-vector-positions lookahead)
 (let ((r (map
	   (lambda (positions)
	    (unshape-matrix
	     (map-vector polar->rect
			 (shape-matrix (list->vector positions) 2))))
	   part-vector-positions)))
  (vector->list
   (map-vector
    (lambda (frame)
     (vector->list
      (unshape-matrix (map-vector rect->polar (shape-matrix frame 2)))))
    (list-of-lists->transposed-matrix
     (map-n
      (lambda (i) (feature-finite-difference
	      (map (lambda (e)(vector-ref e i)) r) lookahead))
      16))))))				;hardwired

(define (unzip l)
 (if (null? l) '()
     (map-n (lambda (i) (map (lambda (e) (list-ref e i)) l)) (length (first l)))))

(define (zip-list l)
 (unless (every (lambda (e) (= (length e) (length (first l)))) l)
  (panic "clip-bad-tracks with tracks of different lengths"))
 (map-n (lambda (i) (map (lambda (e) (list-ref e i)) l)) (length (first l))))

(define (clip-bad-tracks tracks)
 (unzip (remove-if (lambda (boxes) (some dropped-box? boxes)) (zip-list tracks))))

(define (voc4-inside? v box)
 (and (< (voc4-detection-x1 box) (x v) (voc4-detection-x2 box))
    (< (voc4-detection-y1 box) (y v) (voc4-detection-y2 box))))

(define (quad-scale-and-shift quad scale shift)
 (let ((cx (+ (/ (+ (first quad) (third quad)) 2) (x shift)))
       (cy (+ (/ (+ (second quad) (fourth quad)) 2) (y shift)))
       (w (* (- (third quad) (first quad)) (x scale)))
       (h (* (- (fourth quad) (second quad)) (y scale))))
  (list (- cx (/ w 2)) (- cy (/ h 2)) (+ cx (/ w 2)) (+ cy (/ h 2)))))

(define (voc4-box->summary box)
 (list (exact-round (voc4-detection-x1 box))
       (exact-round (voc4-detection-y1 box))
       (exact-round (voc4-detection-x2 box))
       (exact-round (voc4-detection-y2 box))
       (voc4-detection-area box)
       (voc4-detection-model box)
       (voc4-detection-strength box)))

(define (voc4-scale-and-shift box scale shift delta)
 (let* ((center (v+ (voc4-detection-center box) shift))
	(width (* (x scale) (voc4-detection-width box)))
	(height (* (y scale) (voc4-detection-height box))))
  (make-voc4-detection (- (x center) (/ width 2))
		       (- (y center) (/ height 2))
		       (+ (x center) (/ width 2))
		       (+ (y center) (/ height 2))
		       (map
			(lambda (quad) (quad-scale-and-shift quad scale shift))
			(voc4-detection-parts box))
		       (voc4-detection-filter box)
		       (voc4-detection-strength box)
		       delta
		       (voc4-detection-color box)
		       (voc4-detection-model box))))

(define (get-scale-and-shift box klt)
 (let*
   ((klt (remove-if-not
	  (lambda (klt-pair) (voc4-inside? (klt-pair-current klt-pair) box))
	  klt))
    (xs (map - (map (o x klt-pair-next) klt) (map (o x klt-pair-current) klt)))
    (ys (map - (map (o y klt-pair-next) klt) (map (o y klt-pair-current) klt))))
  (if (null? klt)
      (list (vector 1 1) (vector 0 0))
      (list (vector
	     (+ 1 (/ (sqrt (list-variance xs)) (voc4-detection-width box)))
	     (+ 1 (/ (sqrt (list-variance ys)) (voc4-detection-height box))))
	    (vector (list-mean xs) (list-mean ys))))))

(define (voc4-scale-abs box scale)
 (make-voc4-detection
  (* scale (voc4-detection-x1 box))
  (* scale (voc4-detection-y1 box))
  (* scale (voc4-detection-x2 box))
  (* scale (voc4-detection-y2 box))
  (map (lambda (parts) (map (lambda (p) (* scale p)) parts))
       (voc4-detection-parts box))
  (voc4-detection-filter box)
  (voc4-detection-strength box)
  (voc4-detection-delta box)
  (voc4-detection-color box)
  (voc4-detection-model box)))

;; for optical-flow
(define (forward-project-box-scaled
         original-box scaled-transformation delta scale)
 ;; before projecting the box, we rescale the box (which is always in 1280x720
 ;; coordinate) to the video dimension (ie. x scale)
 (let ((box (voc4-scale-abs original-box scale))
       (transformation scaled-transformation))
  (voc4-scale-abs
   (cond
    ((and (list? transformation) (every klt-pair? transformation))
     ;; needs work: changed API
     (let ((scale-and-shift (get-scale-and-shift box transformation)))
      (voc4-scale-and-shift
       box (first scale-and-shift) (second scale-and-shift) delta)))
    (else
     (voc4-scale-and-shift
      box '#(1 1) (average-flow-in-box box transformation) delta)))
   (/ scale))))

(define (box->vector box)
 (vector (voc4-detection-x1 box)
	 (voc4-detection-y1 box)
	 (voc4-detection-x2 box)
	 (voc4-detection-y2 box)))

(define (predict-boxes n boxes-movie transformation-movie scale)
 (unless (= (length boxes-movie) (+ (length transformation-movie) 1))
  (panic "predict-boxes"))
 (let loop ((n n)
	    (prefix '(()))
	    (predicted-boxes-movie boxes-movie)
	    (transformation-movie transformation-movie)
	    (augmented-boxes-movie boxes-movie))
  ;;(unless *quiet-mode?* (format #t "predict boxes: ~s~%" n))
  (if (zero? n)
      augmented-boxes-movie
      (let ((predicted-boxes-movie
	     (map2
	      (lambda (boxes transformation)
	       (map (lambda (box)
		     (forward-project-box-scaled box transformation
                                                 (length prefix)
                                                 scale))
		    boxes))
	      (but-last predicted-boxes-movie)
	      transformation-movie)))
       (loop (- n 1)
	     (cons '() prefix)
	     predicted-boxes-movie
	     (rest transformation-movie)
	     (map2 append
		   (append prefix predicted-boxes-movie)
		   augmented-boxes-movie))))))

(define (update-voc4-strength box strength)
 (make-voc4-detection (voc4-detection-x1 box)
		      (voc4-detection-y1 box)
		      (voc4-detection-x2 box)
		      (voc4-detection-y2 box)
		      (voc4-detection-parts box)
		      (voc4-detection-filter box)
		      strength
		      (voc4-detection-delta box)
		      (voc4-detection-color box)
		      (voc4-detection-model box)))

(define (update-voc4-color box color)
 (make-voc4-detection (voc4-detection-x1 box)
		      (voc4-detection-y1 box)
		      (voc4-detection-x2 box)
		      (voc4-detection-y2 box)
		      (voc4-detection-parts box)
		      (voc4-detection-filter box)
		      (voc4-detection-strength box)
		      (voc4-detection-delta box)
		      color
		      (voc4-detection-model box)))

(define (model-threshold model-name model-path)
 (matlab-eval-strings
  (format #f "load('~a/~a');" model-path model-name)
  "if exist('model'); a=model; elseif exist('csc_model'); a=csc_model; else a='error'; end;"
  "thresh=a.thresh;")
 (matlab-get-double "thresh"))

;;; Likelihood computation

(define (medoids-pathname)
 ;; TODO This is just a temporary location, we should find a naming
 ;; scheme or even better bundle this with the hmm so we never make a
 ;; mistake
 (string-append *video-pathname* "/pose-codebook.sc"))

(define (track-profile-pathname video)
 (generic-root-pathname video "track-profile-data"))

(define (extract-common-features boxes lookahead)
 (let* ((position (map voc4-detection-center boxes))
	(position-x (map x position))
	(position-y (map y position))
	(aspect-ratio (map voc4-detection-aspect-ratio boxes))
	(velocity (feature-finite-difference
		   (map vector position-x position-y) lookahead))
	(acceleration (feature-finite-difference velocity lookahead))
	(area (map voc4-detection-area boxes)))
  (list position-x position-y aspect-ratio
	(feature-finite-difference aspect-ratio lookahead)
	(map magnitude velocity) (map vector-orientation velocity)
	(map magnitude acceleration) (map vector-orientation acceleration)
	area (feature-finite-difference area lookahead))))

;; TODO Without this pose codebook indices for padding frames would be
;; wrong, should be in the trained hmm or at least a parameter
(define *feb-demo:objects*
 '( ;; Summer 2011 objects
   "bag" "baseball-bat" "bench" "bicycle" "big-ball" "bucket" "cage" "car"
   "cardboard-box" "cart" "chair" "closet" "dog" "door" "garbage-can"
   "golf-club" "ladder" "mailbox" "microwave" "motorcycle" "person"
   "person-crawl" "person-down" "pogo-stick" "pylon" "rake" "shovel"
   "skateboard" "small-ball" "suv" "table" "toy-truck" "trailer"
   "trash-bag" "tripod" "trophy" "truck"))

(define *toy-corpus:objects*
 (list "gun" "sign" "giraffe" "ball"))

(define *306-corpus:objects*
 (list "gun" "sign" "giraffe" "person"))

(define *new3-corpus:objects*
 (list "person" "backpack" "chair" "trash-can"))

(define *new4-corpus:objects*
 (list "person" "backpack" "chair" "trash-can" "stool" "traffic-cone"))

(define *general:objects*
 (append *feb-demo:objects*
	 ;; 2012 toy demo objects
	 (list "gun" "sign" "giraffe" "ball" "backpack" "trash-can")))

(define *person-poses*
 '("person" "person-crouch" "person-down"))

(define (num-discrete i type objects)
 (case type
  ((no-pose-no-x-with-cd)
   (case i
    ;; filter indices 2(c)
    ((8 18) 7)
    ;; person-pose indices 2(d)
    ((9 19) (+ 1 (length *person-poses*)))
    (else (fuck-up))))
  ((no-pose-no-x-with-cd)
   (case i
    ;; filter indices 2(c)
    ((8 18) 7)
    ;; person-pose indices 2(d)
    ((9 19) (+ 1 (length *person-poses*)))
    (else (fuck-up))))
  ((no-pose-no-x-with-bcd)
   (case i
    ;; model-indices 2(b)
    ((8 19) (+ 1 (length objects)))
    ;; filter indices 2(c)
    ((9 20) 7)
    ;; person-pose indices 2(d)
    ((10 21) (+ 1 (length *person-poses*)))
    (else (fuck-up))))
  ((discrete-pose-no-x-with-bcd)
   (case i
    ;; cluster indices 2(a)
    ((11 23) 50)
    ;; model-indices 2(b)
    ((8 20) (+ 1 (length objects)))
    ;; filter indices 2(c)
    ((9 21) 7)
    ;; person-pose indices 2(d)
    ((10 22) (+ 1 (length *person-poses*)))
    (else (fuck-up))))
  ((continuous-pose-no-x-with-bcd)
   (case i
    ;; model-indices 2(b)
    ((8 51) (+ 1 (length objects)))
    ;; filter indices 2(c)
    ((9 52) 7)
    ;; person-pose indices 2(d)
    ((10 53) (+ 1 (length *person-poses*)))
    (else (fuck-up))))
  ((combined-pose-no-x)
   (case i
    ;; cluster indices 2(a)
    ((11 55) 50)
    ;; model-indices 2(b)
    ((8 52) (+ 1 (length objects)))
    ;; filter indices 2(c)
    ((9 53) 7)
    ;; person-pose indices 2(d)
    ((10 54) (+ 1 (length *person-poses*)))
    (else (fuck-up))))
  (else (fuck-up))))

(define (*feature-types* single-or-pair type)
 (case type
  ((toy-features)
   (map-n
    (lambda (i)
     (case i
      ((1 3) 'radial)
      ;;((1 3) 'continuous) ;; If you don't care the domain is from -oo to +oo
      (else 'continuous)))
    (case single-or-pair
     ((1) 2)
     ((2) (+ (* 2 2) 1))
     (else (fuck-up)))))
  ((no-pose-no-x)
   (map-n
    (lambda (i)
     (case i
      ((3 5 11 13 18) 'radial)
      ;;((3 5 11 13 18) 'continuous)
      (else 'continuous)))
    (case single-or-pair
     ((1) 8)
     ((2) (+ (* 2 8) 3))
     (else (fuck-up)))))
  ((no-pose-no-x-with-cd)
   (map-n
    (lambda (i)
     (case i
      ((3 5 13 15 22) 'radial)
      ;;((3 5 13 15 22) 'continuous)
      ((8 9 18 19) 'discrete)
      (else 'continuous)))
    (case single-or-pair
     ((1) 10)
     ((2) (+ (* 2 10) 3))
     (else (fuck-up)))))
  ((no-pose-no-x-with-bcd)
   (map-n
    (lambda (i)
     (case i
      ((3 5 14 16 24) 'radial)
      ;;((3 5 14 16 24) 'continuous)
      ((8 9 10 19 20 21) 'discrete)
      (else 'continuous)))
    (case single-or-pair
     ((1) 11)
     ((2) (+ (* 2 11) 3))
     (else (fuck-up)))))
  ((discrete-pose-no-x-with-bcd)
   (map-n
    (lambda (i)
     (case i
      ((3 5 15 17 26) 'radial)
      ;;((3 5 15 17 26) 'continuous)
      ((8 9 10 11 20 21 22 23) 'discrete)
      (else 'continuous)))
    (case single-or-pair
     ((1) 12)
     ((2) (+ (* 2 12) 3))
     (else (fuck-up)))))
  ((continuous-pose-no-x-with-bcd)
   (map-n
    (lambda (i)
     (case i
      ((3 5 46 48 88) 'radial)
      ;;((3 5 46 48 88) 'continuous)
      ((8 9 10 51 52 53) 'discrete)
      (else 'continuous)))
    (case single-or-pair
     ((1) 43)
     ((2) (+ (* 2 43) 3))
     (else (fuck-up)))))
  ((combined-pose-no-x)
   (map-n
    (lambda (i)
     (case i
      ((3 5 47 49 90) 'radial)
      ;;((3 5 47 49 90) 'continuous)
      ((8 9 10 11 52 53 54 55) 'discrete)
      (else 'continuous)))
    (case single-or-pair
     ((1) 44)
     ((2) (+ (* 2 44) 3))
     (else (fuck-up)))))
  (else (fuck-up))))

(define (person-box? box)
 (and (voc4-detection-model box)
    (prefix? "person" (voc4-detection-model box))))

(define (get-closest-medoids boxes medoids)
 (map
  (lambda (box)
   (if (person-box? box)
       (let* ((center (voc4-detection-center box))
	      (normal-parts
	       (list->vector
		(join (map
		       (lambda (part)
			(vector->list
			 (normalize-line-in-voc4-box
			  (make-line-segment (part-center part) center) box)))
		       (voc4-detection-parts box))))))
	(position (minimump medoids (lambda (m) (distance m normal-parts)))
		  medoids))
       (length medoids)))
  boxes))

(define (pairwise-track-features-depraved centers1 centers2 lookahead discard-bad-features?)
 (let* ((center-distance (map distance centers1 centers2))
	(center-velocity (feature-finite-difference center-distance lookahead))
	(center-orientation
	 (map vector-orientation (map v- centers1 centers2))))
  (list-of-lists->transposed-matrix
   (map (lambda (fv)
	 (if discard-bad-features?
	     (drop-last-n-if-possible (- lookahead 1) fv)
	     fv))
	(list center-distance center-velocity center-orientation)))))

(define (pairwise-track-features boxes1 boxes2 lookahead discard-bad-features?)
 (when (or (some dropped-box? boxes1) (some dropped-box? boxes2))
  (panic "Passing in a padding box to the feature vector computation"))
 (pairwise-track-features-depraved (map voc4-detection-center boxes1)
				   (map voc4-detection-center boxes2)
				   lookahead
				   discard-bad-features?))

(define (one-track-features type boxes lookahead medoids
			    objects discard-bad-features? . quantized-flow-params)
 (let*
   ((transformations-movie (delay (first quantized-flow-params)))
    (scale (delay (second quantized-flow-params)))
    (v-d-o-medoids (delay (third quantized-flow-params)))
    (position-l (delay (map voc4-detection-center boxes)))
    (position-x (delay (map x (force position-l))))
    (position-y (delay (map y (force position-l))))
    (velocity
     (delay (feature-finite-difference
             (map vector (force position-x) (force position-y)) lookahead)))
    (flow-in-boxes (delay (map (lambda (box flow)
				(average-flow-in-box
				 (voc4-scale-abs box (force scale))
				 flow))
			       boxes (force transformations-movie))))
    (median-flow-magnitude-in-boxes
     (delay (map (lambda (box flow)
		  (median-flow-in-box
		   (voc4-scale-abs box (force scale))
		   flow))
		 boxes (force transformations-movie))))
    (flow+velocity (delay (map (lambda (v f)
				(k*v 0.5 (v+ v f)))
			       (force velocity) (force flow-in-boxes))))
    (acceleration (delay (feature-finite-difference (force velocity) lookahead)))
    (aspect-ratio (delay (map voc4-detection-aspect-ratio boxes)))
    (aspect-ratio-diff
     (delay (feature-finite-difference (force aspect-ratio) lookahead)))
    (area (delay (map voc4-detection-area boxes)))
    (area-diff (delay (feature-finite-difference (force area) lookahead)))
    (velocity-magnitude (delay (map magnitude (force velocity))))
    (velocity-orientation (delay (map vector-orientation (force velocity))))
    (flow-magnitude-in-boxes (delay (map magnitude (force flow-in-boxes))))
    (flow-orientation-in-boxes (delay (map orientation (force flow-in-boxes))))
    (f+v-magnitude (delay (map magnitude (force flow+velocity))))
    (f+v-orientation (delay (map orientation (force flow+velocity))))
    (acceleration-magnitude (delay (map magnitude (force acceleration))))
    (acceleration-orientation (delay (map vector-orientation (force acceleration))))
    (displacement (delay (map (lambda (b)
			       (if (= (quantize (distance (voc4-detection-center (first boxes))
							  (voc4-detection-center b))
						'dis
						(force v-d-o-medoids))
				      0)
				   0
				   (+ 1
				      (second
				       (minimum-with-position1-f
					(lambda (angle)
					 (angle-separation
					  (vector-orientation (v- (voc4-detection-center b)
								  (voc4-detection-center (first boxes))))
					  angle))
					(list pi (/ pi 2) 0 (- (/ pi 2))))))))
			      boxes)))
    (object-class
     (delay
      (map ;; object class index 2(b)
       (lambda (box)
	(begin
	 (when (not objects) (panic "one-track-features: objects missing"))
	 (let ((model-name (voc4-detection-model box)))
	  (if (equal? model-name "padding")
	      (length objects)
	      (let ((object-index
		     (position (if (person-box? box) "person" model-name)
			       objects)))
	       (unless object-index
		(panic "model-name=~a not in objects-list!" model-name))
	       object-index)))))
       boxes)))
    (root-filter
     (delay
      (map ;; root filter index 2(c)
       (lambda (b)
        (case (voc4-detection-filter b)
         ((1) 0) ((2) 1) ((3) 2) ((4) 3) ((5) 4) ((6) 5) (else 6)))
       boxes)))
    (person-pose-index
     (delay
      (map ;; person pose index 2(d)
       (lambda (box)
        (let ((model-name (voc4-detection-model box)))
         (if (person-box? box) ;; <==> (prefix? "person" model-name)
             (let ((person-index (position model-name *person-poses*)))
              (if (equal? person-index #f)
                  (panic
                   (format #f "model-name=~a not in *person-poses*!"
                           model-name))
                  person-index))
             (length *person-poses*))))
       boxes)))
    (continuous-part-displacements^T
     (delay
      (if (person-boxes? boxes)
          (get-part-vectors boxes)
          (map-n (lambda (_) (map-n (lambda (_) 0) 16)) (length boxes)))))
    (continuous-part-displacements-diff
     (delay (transpose-list-of-lists
             (get-changes-in-part-vectors
              (force continuous-part-displacements^T)
              lookahead))))
    (continuous-part-displacements
     (delay (transpose-list-of-lists (force continuous-part-displacements^T))))
    (medoid (delay (get-closest-medoids boxes medoids)))
    (standard-features
     (list aspect-ratio aspect-ratio-diff
           velocity-magnitude velocity-orientation
           acceleration-magnitude acceleration-orientation
           area area-diff))
    (add-continuous-parts
     (lambda (l) (append l
			 (force continuous-part-displacements)
			 (force continuous-part-displacements-diff)))))
  (list-of-lists->transposed-matrix
   (map (lambda (fv)
	 (let ((eval-fv (if (procedure? fv)
			    (force fv)
			    fv)))
	  (if discard-bad-features?
	      (drop-last-n-if-possible (- lookahead 1) eval-fv)
	      eval-fv)))
        (case type
	 ((pos-x) `(,position-x))
         ((no-pose non-pose) `(,position-x ,position-y ,@standard-features))
         ((no-pose-no-x) `(,@standard-features))
         ((no-pose-with-bcd)
          `(,position-x ,position-y ,@standard-features
                        ,object-class ,root-filter ,person-pose-index))
	 ((no-pose-no-x-with-cd)
	  `(,@standard-features ,root-filter ,person-pose-index))
	 ((no-pose-no-x-with-bcd)
	  `(,@standard-features ,object-class ,root-filter ,person-pose-index))
         ((continuous-pose)
          (add-continuous-parts `(,position-x ,position-y ,@standard-features)))
         ((continuous-pose-no-x) (add-continuous-parts standard-features))
         ((continuous-pose-with-bcd)
          (add-continuous-parts
           `(,position-x ,position-y ,@standard-features
                         ,object-class ,root-filter ,person-pose-index)))
         ((continuous-pose-no-x-with-bcd)
          (add-continuous-parts
           `(,@standard-features ,object-class ,root-filter ,person-pose-index)))
         ((discrete-pose feb-demo-discrete-pose)
          `(,position-x ,position-y ,@standard-features
                        ,medoid ,object-class ,root-filter))
         ((discrete-pose-no-x)
          `(,@standard-features ,medoid ,object-class ,root-filter))
         ((discrete-pose-with-bcd)
          `(,position-x ,position-y ,@standard-features
                        ,object-class ,root-filter ,person-pose-index ,medoid))
         ((discrete-pose-no-x-with-bcd)
          `(,@standard-features ,object-class ,root-filter ,person-pose-index ,medoid))
         ((combined-pose)
          (add-continuous-parts
           `(,position-x ,position-y ,@standard-features ,object-class
                         ,root-filter ,person-pose-index ,medoid)))
         ((combined-pose-no-x)
          (add-continuous-parts
           `(,@standard-features ,object-class ,root-filter
                                 ,person-pose-index ,medoid)))
         ((vel-orientation) `(,velocity-orientation))
         ((vel-magnitude) `(,velocity-magnitude))
	 ((flow-magnitude-in-box) `(,flow-magnitude-in-boxes))
	 ((flow-orientation-in-box) `(,flow-orientation-in-boxes))
	 ((f+v-magnitude) `(,f+v-magnitude))
	 ((f+v-orientation) `(,f+v-orientation))
	 ((median-flow-magnitude-in-box) `(,median-flow-magnitude-in-boxes))
	 ((displacement) `(,displacement))
         ((model-name-agent) (list object-class))
	 ((model-color-agent)
	  (transpose-list-of-lists
	   (map
	    ;; takes h as radian value from hsv
	    (lambda (b)
	     (take 1 (vector->list
		      (hsv-degrees->hsv-radians
		       (unpack-color (voc4-detection-color b))))))
	    boxes)))
	 ((model-color-agenten-quantized)
	  (transpose-list-of-lists
	   (map (lambda (a)
		 (list (quantize (first a) 'color (force v-d-o-medoids))))
		(map
		 ;; takes h as radian value from hsv
		 (lambda (b)
		  (take 1 (vector->list
			   (hsv-degrees->hsv-radians
			    (unpack-color (voc4-detection-color b))))))
		 boxes))))
         (else (panic "one-track-features unknown type ~a" type)))))))

(define (quantize value type medoids)
 (let* ((fake-Mahalanobis
	 (lambda (e miu sigma)
	  (/ (abs (- e miu)) (sqrt sigma))))
	(my-angle-separation
	 (lambda (e miu sigma)
	  (angle-separation e miu)))
	(nearest-1d
	 (lambda (e seeds dist-f)
	  (second
	   (minimum-with-position1-f
	    (lambda (dist-and-center)
	     (if (and (not (equal? dist-f my-angle-separation))
		    (some (lambda (s)
			   (or (< (second dist-and-center) (first s) e)
			      (< e (first s) (second dist-and-center))))
			  seeds))
		 infinity
		 (first dist-and-center)))
	    (zip (map (lambda (s)
		       (dist-f e (first s) (second s))) seeds)
		 (map first seeds)))))))
  (case type
   ((v) (nearest-1d value (second (assoc 'velocity medoids)) fake-Mahalanobis))
   ((d) (nearest-1d value (second (assoc 'distance medoids)) fake-Mahalanobis))
   ((dd) (nearest-1d value (second (assoc 'distance-derivative medoids)) fake-Mahalanobis))
   ((o) (nearest-1d value (second (assoc 'orientation medoids)) my-angle-separation))
   ((dis) (nearest-1d value (second (assoc 'displacement medoids)) fake-Mahalanobis))
   ((r) (nearest-1d value (second (assoc 'box-ratio medoids)) fake-Mahalanobis))
   ((color) (nearest-1d value (second (assoc 'color medoids)) my-angle-separation))
   (else (panic "verb-features-quantized")))))

(define (two-track-features-f f1 f2 type track1 track2 lookahead medoids
			      objects discard-bad-features? . quantized-flow-params)
 (unless (= (length track1) (length track2))
  (panic "Trying to compute a feature vector for tracks of unequal length"))
 (let ((transformations-movie (delay (first quantized-flow-params)))
       (scale (delay (second quantized-flow-params)))
       (v-d-o-medoids (delay (third quantized-flow-params))))
  (case type
   ((toy-features)
    (map-vector vector-append
		(f1 'vel-magnitude track1 lookahead medoids objects discard-bad-features?)
		(f1 'vel-orientation track1 lookahead medoids objects discard-bad-features?)
		(f1 'vel-magnitude track2 lookahead medoids objects discard-bad-features?)
		(f1 'vel-orientation track2 lookahead medoids objects discard-bad-features?)
		(list-of-lists->transposed-matrix
		 (map (lambda (fv)
		       (if discard-bad-features?
			   (drop-last-n-if-possible (- lookahead 1) fv)
			   fv))
		      (list (map distance
				 (map voc4-detection-center track1)
				 (map voc4-detection-center track2)))))))
   ((verb-features-raw)
    ;; DEBUGGING
    (map-vector vector-append
		(f1 'flow-magnitude-in-box track1
		    lookahead medoids objects discard-bad-features?
		    (force transformations-movie) (force scale))
		(f1 'flow-magnitude-in-box track2
		    lookahead medoids objects discard-bad-features?
		    (force transformations-movie) (force scale))))
   ((verb-features)
    ;; * 10 is required because there are numerical errors with gaussians
    ;; stationary (0) or moving (10)
    (define (velocity-sigmoid t) (* 10 (sigmoid t 25 (/ 5))))
    ;; near (0) or far (10)
    (define (distance-sigmoid t) (* 10 (sigmoid t 300 (/ 30))))
    ;; away from (0) or stationary (10)
    (define (relative-velocity-sigmoid t) (* 10 (sigmoid t -20 (/ 3))))
    (map-vector vector-append
		;; DEBUGGING
		(map-vector (lambda (a) (vector (velocity-sigmoid (x a))))
			    (f1 'vel-magnitude track1 lookahead medoids discard-bad-features?))
		(f1 'vel-orientation track1 lookahead medoids objects discard-bad-features?)
		;; DEBUGGING
		(map-vector (lambda (a) (vector (velocity-sigmoid (x a))))
			    (f1 'vel-magnitude track2 lookahead medoids discard-bad-features?))
		(f1 'vel-orientation track2 lookahead medoids objects discard-bad-features?)
		;; DEBUGGING
		(map-vector
		 (lambda (a b) (vector (distance-sigmoid (distance a b))))
		 (f1 'pos-x track1 lookahead medoids objects discard-bad-features?)
		 (f1 'pos-x track2 lookahead medoids objects discard-bad-features?))
		(map-vector (lambda (a) (vector (relative-velocity-sigmoid (x a))))
			    (list-of-lists->transposed-matrix
			     ;; DEBUGGING
			     (map (lambda (fv)
				   (if discard-bad-features?
				       (drop-last-n-if-possible (- lookahead 1) fv)
				       fv))
				  (list
				   (feature-finite-difference
				    (map
				     (lambda (a b) (distance `#(,a) `#(,b)))
				     (map (o x voc4-detection-center)
					  (if (cached-box? (first track1))
					      (map cached-box-voc4 track1)
					      track1))
				     (map (o x voc4-detection-center)
					  (if (cached-box? (first track2))
					      (map cached-box-voc4 track2)
					      track2)))
				    lookahead)))))))
   ((verb-features-quantized)
    (map-vector vector-append
		(map-vector (lambda (a) (vector (quantize (x a) 'v (force v-d-o-medoids))))
			    (f1 'flow-magnitude-in-box track1
				lookahead medoids objects discard-bad-features?
				(force transformations-movie) (force scale)))
		(map-vector (lambda (a) (vector (quantize (x a) 'o (force v-d-o-medoids))))
			    (f1 'flow-orientation-in-box track1
				lookahead medoids objects discard-bad-features?
				(force transformations-movie) (force scale)))
		(map-vector (lambda (a) (vector (quantize (x a) 'v (force v-d-o-medoids))))
			    (f1 'flow-magnitude-in-box track2
				lookahead medoids objects discard-bad-features?
				(force transformations-movie) (force scale)))
		(map-vector (lambda (a) (vector (quantize (x a) 'o (force v-d-o-medoids))))
			    (f1 'flow-orientation-in-box track2
				lookahead medoids objects discard-bad-features?
				(force transformations-movie) (force scale)))
		(map-vector
		 (lambda (a b) (vector (quantize (distance a b) 'd (force v-d-o-medoids))))
		 (f1 'pos-x track1 lookahead medoids objects discard-bad-features?)
		 (f1 'pos-x track2 lookahead medoids objects discard-bad-features?))
		(list-of-lists->transposed-matrix
		 (map (lambda (fv)
		       (if discard-bad-features?
			   (drop-last-n-if-possible (- lookahead 1) fv)
			   fv))
		      (list
		       (map
			(lambda (a b)
			 (- 1 (quantize (/ (voc4-detection-area a)
					   (voc4-detection-area b)) 'r (force v-d-o-medoids))))
			(if (cached-box? (first track1))
			    (map cached-box-voc4 track1)
			    track1)
			(if (cached-box? (first track2))
			    (map cached-box-voc4 track2)
			    track2)))))))
   ((patient-velocity-direction) (f1 'vel-orientation track2 lookahead medoids objects discard-bad-features?))
   ((agent-patient-distance-derivative)
    (define (relative-velocity-towards-sigmoid t) (* 10 (sigmoid t -20 (- (/ 3)))))
    (define (relative-velocity-away-from-sigmoid t) (* 10 (sigmoid t 20 (/ 3))))
    (map-vector (lambda (a) (vector (relative-velocity-towards-sigmoid (x a))
			       (relative-velocity-away-from-sigmoid (x a))))
		(list-of-lists->transposed-matrix
		 ;; DEBUGGING
		 (map (lambda (fv)
		       (if discard-bad-features?
			   (drop-last-n-if-possible (- lookahead 1) fv)
			   fv))
		      (list
		       (feature-finite-difference
			(map
			 (lambda (a b) (distance `#(,a) `#(,b)))
			 (map (o x voc4-detection-center)
			      (if (cached-box? (first track1))
				  (map cached-box-voc4 track1)
				  track1))
			 (map (o x voc4-detection-center)
			      (if (cached-box? (first track2))
				  (map cached-box-voc4 track2)
				  track2)))
			lookahead))))))
   ((agent-patient-distance-derivative-quantized)
    (map-vector vector-append
		(map-vector (lambda (a) (vector (quantize (x a) 'v (force v-d-o-medoids))))
			    (f1 'flow-magnitude-in-box track1
				lookahead medoids objects discard-bad-features?
				(force transformations-movie) (force scale)))
		(map-vector
		 (lambda (a b) (vector (quantize (distance a b) 'd (force v-d-o-medoids))))
		 (f1 'pos-x track1 lookahead medoids objects discard-bad-features?)
		 (f1 'pos-x track2 lookahead medoids objects discard-bad-features?))))
   ((agent-patient-distance-derivative-raw)
    (map-vector vector-append
		(list-of-lists->transposed-matrix
		 (map (lambda (fv)
		       (if discard-bad-features?
			   (drop-last-n-if-possible (- lookahead 1) fv)
			   fv))
		      (list
		       (feature-finite-difference
			(map
			 (lambda (a b) (distance `#(,a) `#(,b)))
			 (vector->list
			  (reduce vector-append
				  (vector->list
				   (f1 'pos-x track1 lookahead medoids
				       objects discard-bad-features?)) '#()))
			 (vector->list
			  (reduce vector-append
				  (vector->list
				   (f1 'pos-x track2 lookahead medoids
				       objects discard-bad-features?)) '#())))
			lookahead)
		       )))
		(map-vector
		 (lambda (a b) (vector (distance a b)))
		 (f1 'pos-x track1 lookahead medoids objects discard-bad-features?)
		 (f1 'pos-x track2 lookahead medoids objects discard-bad-features?))))
   ((agent-patient-velocities)
    ;; nothing (0) or fast (10)
    (define (velocity-fast-sigmoid t) (* 10 (sigmoid t 20 (/ 3))))
    ;; nothing (0) or slow (10)
    (define (velocity-slow-sigmoid t) (* 10 (sigmoid t 40 (/ 3))))
    (map-vector vector-append
		;; DEBUGGING
		(map-vector (lambda (a) (vector (velocity-fast-sigmoid (x a))))
			    (f1 'vel-magnitude track1 lookahead medoids objects discard-bad-features?))
		;; DEBUGGING
		(map-vector (lambda (a) (vector (velocity-slow-sigmoid (x a))))
			    (f1 'vel-magnitude track1 lookahead medoids objects discard-bad-features?))
		;; DEBUGGING
		(map-vector (lambda (a) (vector (velocity-fast-sigmoid (x a))))
			    (f1 'vel-magnitude track2 lookahead medoids objects discard-bad-features?))
		;; DEBUGGING
		(map-vector (lambda (a) (vector (velocity-slow-sigmoid (x a))))
			    (f1 'vel-magnitude track2 lookahead medoids objects discard-bad-features?))))
   ((agent-patient-velocities-quantized)
    (map-vector vector-append
		;; (map-vector (lambda (a) (vector (quantize (x a) 'v (force v-d-o-medoids))))
		;; 	    (f1 'flow-magnitude-in-box track1
		;; 		lookahead medoids objects discard-bad-features?
		;; 		(force transformations-movie) (force scale)))
		(map-vector (lambda (a) (vector (quantize (x a) 'v (force v-d-o-medoids))))
			    (f1 'flow-magnitude-in-box track2
				lookahead medoids objects discard-bad-features?
				(force transformations-movie) (force scale)))))
   ((agent-patient-orientation)
    (map-vector (lambda (a) (vector (z a))) (f2 track1 track2 lookahead medoids objects discard-bad-features?)))
   ((agent-patient-x-orientation-quantized)
    (list-of-lists->transposed-matrix
     (map (lambda (fv)
	   (if discard-bad-features?
	       (drop-last-n-if-possible (- lookahead 1) fv)
	       fv))
	  (list
	   (map
	    (lambda (a b)
	     (cond
	      ((< a (- b 50)) 0)
	      ((> a (+ b 50)) 1)
	      (else 2)))
	    (map (o x voc4-detection-center)
		 (if (cached-box? (first track1))
		     (map cached-box-voc4 track1)
		     track1))
	    (map (o x voc4-detection-center)
		 (if (cached-box? (first track2))
		     (map cached-box-voc4 track2)
		     track2)))))))
   ((model-name-agent) (f1 type track1 lookahead medoids objects discard-bad-features?))
   ((model-name-patient) (f1 'model-name-agent track2 lookahead medoids objects discard-bad-features?))
   ((model-color-agent) (f1 type track1 lookahead medoids objects discard-bad-features?))
   ((model-color-patient) (f1 'model-color-agent track2 lookahead medoids objects discard-bad-features?))
   ((model-color-agent-quantized)
    (f1 type track1 lookahead medoids objects discard-bad-features? #f #f (force v-d-o-medoids)))
   ((model-color-patient-quantized)
    (f1 'model-color-agent-quantized track2 lookahead medoids objects discard-bad-features?
	#f #f (force v-d-o-medoids)))
   (else (map-vector vector-append
		     (f1 type track1 lookahead medoids objects discard-bad-features?)
		     (f1 type track2 lookahead medoids objects discard-bad-features?)
		     (f2 track1 track2 lookahead medoids objects discard-bad-features?))))))

;; DEPRAVED use two-track-features-c instead
(define (two-track-features type track1 track2 lookahead medoids objects
			    discard-bad-features? . quantized-flow-params)
 (apply two-track-features-f `(,one-track-features
			       ,pairwise-track-features
			       ,type ,track1 ,track2 ,lookahead ,medoids
			       ,objects ,discard-bad-features? ,@quantized-flow-params)))

(define (three-track-features-f f1 f2 type track1 track2 track3
				lookahead medoids objects
				discard-bad-features? . quantized-flow-params)
 ;; ANDREI WARNING, this is only for the sentence tracker,
 ;; don't use it elsewhere, to be replaced
 ;; TODO This is incomplete, it defaults to acting like
 ;; two-track-features, when it shouldn't
 (case type
  ((model-name-other) (f1 'model-name-agent track3 lookahead medoids
			  objects discard-bad-features?))
  ((model-color-other) (f1 'model-color-agent track3 lookahead medoids
			   objects discard-bad-features?))
  ((agent-location-x-orientation-quantized)
   (two-track-features-f
    f1 f2 'agent-patient-x-orientation-quantized track1 track3
    lookahead medoids objects discard-bad-features?))
  ((patient-location-x-orientation-quantized)
   (two-track-features-f
    f1 f2 'agent-patient-x-orientation-quantized track2 track3
    lookahead medoids objects discard-bad-features?))
  ((agent-location-distance-derivative-quantized)
   (apply two-track-features-f
	  `(,f1 ,f2 agent-patient-distance-derivative-quantized ,track1 ,track3
		,lookahead ,medoids ,objects ,discard-bad-features? ,@quantized-flow-params)))
  (else
   (apply two-track-features-f
	  `(,f1 ,f2 ,type ,track1 ,track2
		,lookahead ,medoids ,objects ,discard-bad-features?
		,@quantized-flow-params)))))

;; DEPRAVED this is indeed two track features
;; use two track features combined with machine roles
(define (three-track-features type track1 track2 track3 lookahead medoids
			      objects discard-bad-features? . quantized-flow-params)
 ;; ANDREI WARNING, this is only for the sentence tracker,
 ;; don't use it elsewhere, to be replaced
 (apply three-track-features-f
	`(,one-track-features
	  ,pairwise-track-features
	  ,type ,track1 ,track2 ,track3 ,lookahead ,medoids
	  ,objects ,discard-bad-features? ,@quantized-flow-params)))

;; Bindings for c functions
(define allocate-feature-medoid
 (c-function pointer ("allocFeatureMedoid" int)))

(define set-feature-medoid-name!
 (c-function void ("set_feature_medoid_name" pointer string)))

(define set-feature-medoid-mean!
 (c-function void ("set_feature_medoid_mean" pointer int double)))

(define set-feature-medoid-sigma!
 (c-function void ("set_feature_medoid_sigma" pointer int double)))

(define set-feature-medoid-dm-id!
 (c-function void ("set_feature_medoid_dm_id" pointer int)))

(define free-feature-medoid
 (c-function void ("freeFeatureMedoid" pointer)))

(define free-features
 (c-function void ("freeFeatures" pointer)))

(define set-track-boxes!
 (c-function void ("set_track_boxes" pointer int pointer)))

(define allocate-track
 (c-function pointer ("allocTrack" int)))

(define free-track
 (c-function void ("freeTrack" pointer)))

(define set-boxes-movie!
 (c-function void ("set_boxes_movie" pointer int int pointer)))

(define allocate-boxes-movie
 (c-function pointer ("allocBoxesMovie" int int)))

(define free-boxes-movie
 (c-function void ("freeBoxesMovie" pointer)))

(define one-track-features-c-external
 (c-function pointer ("one_track_features" int pointer int int
		      pointer pointer int int pointer int double pointer int)))

(define word-features-c-external
 (c-function pointer ("word_features" int pointer pointer pointer int unsigned
		      pointer pointer int int pointer int double pointer int)))

(define new-feature-c-external
 (c-function pointer ("new_feature" int pointer pointer pointer pointer
		      int pointer double pointer int)))

(define print-c-features
 (c-function void ("print_Features" pointer)))

(define c-features-tt
 (c-function unsigned ("Features_tt" pointer)))

(define c-features-ii
 (c-function unsigned ("Features_ii" pointer)))

(define c-features-v
 (c-function pointer ("Features_v" pointer)))

;; Compute one track features from c code
(define (one-track-features-c type track lookahead raw? objects
			      discard-bad-features? flows-movie scale feature-medoids)
 (let* ((tt (length track))
	(flows-movie (pad-with-last-if-necessary flows-movie tt))
	(c-boxes (map (lambda (d)
		       (voc4-detection->box d)) track))
	(c-track (allocate-track tt))
	(c-feature-medoids (map (lambda (fm) (feature-medoid->c-feature-medoid fm))
				feature-medoids))
	(c-flow-struct-movie (map (lambda (fl) (optical-flow->c-flow-struct fl))
				  flows-movie))
	(c-features (begin (with-c-pointers
			    (lambda (c-boxes-array)
			     (set-track-boxes! c-track tt c-boxes-array))
			    (list->vector c-boxes))
			   (with-c-strings
			    (lambda (objects-array)
			     (with-c-pointers
			      (lambda (flows-array)
			       (with-c-pointers
				(lambda (c-feature-medoids-array)
				 (one-track-features-c-external
				  type c-track lookahead (if raw? 1 0) 0 objects-array
				  (length objects) (if discard-bad-features? 1 0) flows-array
				  (length flows-movie) scale c-feature-medoids-array
				  (length feature-medoids)))
				(list->vector c-feature-medoids)))
			      (list->vector c-flow-struct-movie)))
			    (list->vector objects))))
	(features (c-features->features c-features)))
  ;;
  (for-each free-box c-boxes)
  (free-track c-track)
  (for-each free-feature-medoid c-feature-medoids)
  (for-each free-c-flow-struct c-flow-struct-movie)
  (free-features c-features)
  features))


;; Compute word features from c code
(define (word-features-c type track1 track2 track3 lookahead raw objects
			 discard-bad-features? flows-movie scale feature-medoids)
 (let* ((tt (length track1))
	(flows-movie (pad-with-last-if-necessary flows-movie tt))
	(c-boxes1 (map (lambda (d)
			(voc4-detection->box d)) track1))
	(c-boxes2 (map (lambda (d)
			(voc4-detection->box d)) track2))
	(c-boxes3 (map (lambda (d)
			(voc4-detection->box d)) track3))
	(c-track1 (allocate-track tt))
	(c-track2 (if (null? c-boxes2) 0 (allocate-track tt)))
	(c-track3 (if (null? c-boxes3) 0 (allocate-track tt)))
	(c-feature-medoids (map (lambda (fm) (feature-medoid->c-feature-medoid fm))
				feature-medoids))
	(c-flow-struct-movie (map (lambda (fl) (optical-flow->c-flow-struct fl))
				  flows-movie))
	(c-features (begin (with-c-pointers
			    (lambda (c-boxes-array)
			     (set-track-boxes! c-track1 tt c-boxes-array))
			    (list->vector c-boxes1))
			   (unless (null? c-boxes2)
			    (with-c-pointers
			     (lambda (c-boxes-array)
			      (set-track-boxes! c-track2 tt c-boxes-array))
			     (list->vector c-boxes2)))
			   (unless (null? c-boxes3)
			    (with-c-pointers
			     (lambda (c-boxes-array)
			      (set-track-boxes! c-track3 tt c-boxes-array))
			     (list->vector c-boxes3)))
			   (with-c-strings
			    (lambda (objects-array)
			     (with-c-pointers
			      (lambda (flows-array)
			       (with-c-pointers
				(lambda (c-feature-medoids-array)
				 (word-features-c-external
				  type c-track1 c-track2 c-track3 lookahead raw 0 objects-array
				  (length objects) discard-bad-features? flows-array
				  (length flows-movie) scale c-feature-medoids-array
				  (length feature-medoids)))
				(list->vector c-feature-medoids)))
			      (list->vector c-flow-struct-movie)))
			    (list->vector objects))))
	(features (c-features->features c-features)))
  ;;
  (for-each free-box c-boxes1)
  (unless (null? c-boxes2)
   (for-each free-box c-boxes2))
  (unless (null? c-boxes3)
   (for-each free-box c-boxes3))
  (free-track c-track1)
  (unless (null? c-boxes2) (free-track c-track2))
  (unless (null? c-boxes3) (free-track c-track3))
  (for-each free-feature-medoid c-feature-medoids)
  (for-each free-c-flow-struct c-flow-struct-movie)
  (free-features c-features)
  features))

(define (new-feature type lookahead new-box1 new-box2 new-box3 objects
		     new-transformation scale c-feature-medoids)
 (let* ((new-box1 (voc4-detection->box new-box1))
	(new-box2 (if new-box2 (voc4-detection->box new-box2) 0))
	(new-box3 (if new-box3 (voc4-detection->box new-box3) 0))
	(new-c-flow (optical-flow->c-flow-struct new-transformation))
	(r (with-c-strings
	    (lambda (objects-array)
	     (with-c-pointers
	      (lambda (feature-medoids-array)
	       (new-feature-c-external type new-box1 new-box2 new-box3
				       objects-array (length objects) new-c-flow
				       scale feature-medoids-array (length c-feature-medoids)))
	      (list->vector c-feature-medoids)))
	    (list->vector objects)))
	(feature (c-features->features r)))
  (free-box new-box1)
  (unless (zero? new-box2) (free-box new-box2))
  (unless (zero? new-box3) (free-box new-box3))
  (free-c-flow-struct new-c-flow)
  (free-features r)
  (vector-ref feature 0)))

;; Convert c track features to vector of vectors
(define (c-features->features c-features)
 (let* ((tt (c-features-tt c-features))
	(ii (c-features-ii c-features))
	(c-pointers
	 (c-exact-array->vector (c-features-v c-features) c-sizeof-long tt #f)))
  (map-vector (lambda (ptr)
	       (c-inexact-array->vector ptr c-sizeof-double ii #t))
	      c-pointers)))

(define (features-log-likelihood features trained-hmm)
 (let* ((rmat (features-vectors->rmat features))
	(log-likelihood
	 (model-log-likelihood (trained-hmm-model trained-hmm) rmat)))
  (free-rmat rmat)
  log-likelihood))

(define (features->rmat features)
 (let ((rmat (allocate-rmat (length (car features)) (length features))))
  (for-each-indexed
   (lambda (feature-vector time)
    (for-each-indexed (lambda (feature i)
		       (rmat-set! rmat i time feature))
		      feature-vector))
   features)
  rmat))

(define (features-vectors->rmat features)
 (let* ((rmat (allocate-rmat (vector-length (x features))
			     (vector-length features))))
  (for-each-indexed-vector
   (lambda (feature-vector time)
    (for-each-indexed-vector
     (lambda (feature i) (rmat-set! rmat i time feature))
     feature-vector))
   features)
  rmat))

(define (map-all-unordered-pairs f l)
 (map (lambda (e) (f (first e) (second e))) (all-pairs l)))
(define (map-all-ordered-pairs f l)
 (join (map (lambda (a) (map (lambda (b) (f a b)) (remove a l))) l)))

(define (read-hmm filename)
 (define (normalise-row r)
  (let* ((v 1e-300) (l (map (lambda (a) (max a v)) r)) (s (reduce + l 0)))
   (map (lambda (e) (/ e s)) l)))
 (define (normalize-psi-parameters psi)
  (set-psi-parameters!
   psi
   (map-vector
    (lambda (s)
     (map-indexed-vector
      (lambda (d i) (if (equal? (first d) 'discrete) ;(or (= i 11) (= i 24))
		   (cons 'discrete (normalise-row (rest d)))
		   d))
      s))
    (psi-parameters psi)))
  (set-psi-a!
   psi
   (list->vector
    (map-indexed
     (lambda (r i)
      (vector-append
       (subvector r 0 i)
       (list->vector
	(normalise-row (vector->list (subvector r i (vector-length r)))))))
     (vector->list (psi-a psi)))))
  (set-psi-b! psi (list->vector (normalise-row (vector->list (psi-b psi)))))
  psi)
 (let ((trained-hmm (read-object-from-file
		     (expand-filename (default-extension filename "sc")))))
  ;; Check for legacy hmm models
  (unless (trained-hmm? trained-hmm)
   ;; feature type is one of:
   ;;  non-pose, continuous-pose, continuous-pose-no-x, discrete-pose
   ;; training type is one of: ml or dt
   (panic "You are trying to read in an hmm in the old format.
	   The new format adds three fields at the end containing the number of participants, feature type, and training type"))
  (set-trained-hmm-model!
   trained-hmm
   (psi->model (normalize-psi-parameters (trained-hmm-model trained-hmm))))
  trained-hmm))

(define (compute-likelihoods video named-tracks models medoids objects
			     likelihoods-cache-f unordered-pairs?)
 (let ((models-features-1
	(remove-if-not (lambda (m) (= (trained-hmm-participants m) 1)) models))
       (models-features-2
	(remove-if-not (lambda (m) (= (trained-hmm-participants m) 2)) models))
       (video-name (any-video->string video)))
  (define (compute-likelihood video track-names feature-vector
			      models named-tracks)
   (map
    (lambda (model)
     (make-result
      video-name
      track-names
      (trained-hmm-feature-type model)
      (trained-hmm-states model)
      (trained-hmm-verb model)
      (if (= (vector-length feature-vector) 0)
	  (- infinity)
	  (features-log-likelihood feature-vector model))
      named-tracks))
    models))
  (append
   (map
    (lambda (named-track)
     (likelihoods-cache-f
      video (list named-track) models-features-1
      (lambda () (compute-likelihood
	     video
	     (list (first named-track))
	     (let ((a (one-track-features
		       (trained-hmm-feature-type (first models))
		       (first (clip-bad-tracks (list (second named-track))))
		       2 medoids objects #t)))
	      (pp a)(newline)
	      a)
	     models-features-1 (list named-track)))))
    named-tracks)
   ((if unordered-pairs?
	map-all-unordered-pairs
	map-all-ordered-pairs)
    (lambda (named-track-1 named-track-2)
     (likelihoods-cache-f
      video (list named-track-1 named-track-2) models-features-2
      (lambda ()
       (let ((tracks (clip-bad-tracks
		      (list (second named-track-1) (second named-track-2)))))
	(compute-likelihood
	 video
	 (list (first named-track-1) (first named-track-2))
	 (two-track-features (trained-hmm-feature-type (first models))
			     (first tracks) (second tracks) 2 medoids objects #t)
	 models-features-2 (list named-track-1 named-track-2))))))
    named-tracks))))

(define (compute-likelihoods-from-files
	 video model-names-file
	 medoids objects
	 track-filter precomputed-likelihoods-f
	 unordered-pairs?)
 (compute-likelihoods
  video
  (map
   (lambda (available)
    (string-append (string-join "-" (but-last available)) "." (last available))
    (read-voc4-overgenerated-smooth-tracked-boxes
     video (second available) (third available)))
   (remove-if track-filter
	      (video-voc4-overgenerated-smooth-tracked-boxes-available video)))
  (map read-hmm (remove "" (read-file model-names-file)))
  medoids
  objects
  precomputed-likelihoods-f
  unordered-pairs?))

;;; Viterbi tracker

(define (box-distance box1 box2)
 ;; This is what the Matlab code does but we believe that it should do
 ;; (distance (box->vector box1) (box->vector box2)).
 ;; Once upon a time we used squared distance, but it seems to work
 ;; more poorly than euclidean distance
 ;; (let* ((b1 (voc4-detection-center box1)) (b2 (voc4-detection-center box2)))
 ;;  (+ (sqr (- (x b2) (x b1)))
 ;;     (sqr (- (y b2) (y b1)))))
 (distance (voc4-detection-center box1) (voc4-detection-center box2)))

(define (box-pair-cost box1 transformation box2 scale)
 (box-distance (forward-project-box-scaled box1 transformation 0
					   scale) box2))

(define (box-cost box) (voc4-detection-strength box))

(define (box-similarity box1 box2)
 (if (equal? (voc4-detection-model box1)
	     (voc4-detection-model box2))
     0
     minus-infinity))

(define (position-of-first-valid-box boxes-movie threshold)
 (position-if (lambda (boxes) (some-valid-box? boxes threshold)) boxes-movie))

(define (update-voc4-model box model)
 (make-voc4-detection (voc4-detection-x1 box)
		      (voc4-detection-y1 box)
		      (voc4-detection-x2 box)
		      (voc4-detection-y2 box)
		      (voc4-detection-parts box)
		      (voc4-detection-filter box)
		      (voc4-detection-strength box)
		      (voc4-detection-delta box)
		      (voc4-detection-color box)
		      model))

(define (voc4-center a)
 (vector (/ (- (voc4-detection-x2 a) (voc4-detection-x1 a)) 2)
	 (/ (- (voc4-detection-y2 a) (voc4-detection-y1 a)) 2)))

(define (log-discrete x distribution)
 (log (* (vector-ref distribution x)
	 (vector-length distribution))))

(define (compute-features-cost features distributions)
 (unless (= (vector-length features) (vector-length distributions))
  (pp features) (newline)
  (pp distributions) (newline)
  (fuck-up))
 (map-reduce-vector
  +
  0
  (lambda (feature distribution)
   (case (first distribution)
    ((CONTINUOUS) (log-univariate-gaussian
                   feature (second distribution) (third distribution)))
    ((TRUNCATED-NORMAL)
     (log
      (truncated-normal-distribution-pdf feature (second distribution) (third distribution)
                                         (fourth distribution) (fifth distribution))))
    ((RADIAL)
     (begin
      (unless (<= (- pi) feature pi) (fuck-up))
      (log-von-mises
       feature (second distribution) (third distribution))))
    ((DISCRETE) (log-discrete feature (list->vector (rest distribution))))
    (else (fuck-up))))
  features
  distributions))

;; DEBUGGING
(define (compute-features-costs features distributions)
 (unless (= (vector-length features) (vector-length distributions))
  (pp features) (newline)
  (pp distributions) (newline)
  (fuck-up))
 (map-vector
  (lambda (feature distribution)
   (case (first distribution)
    ('CONTINUOUS (log-univariate-gaussian
                  feature (second distribution) (third distribution)))
    ('TRUNCATED-NORMAL
     (log
      (truncated-normal-distribution-pdf feature (second distribution) (third distribution)
                                         (fourth distribution) (fifth distribution))))
    ('RADIAL
     (begin
      (unless (<= (- pi) feature pi) (fuck-up))
      (log-von-mises
       feature (second distribution) (third distribution))))
    ('DISCRETE (log-discrete feature (list->vector (rest distribution))))
    (else (fuck-up))))
  features
  distributions))

(define (get-feature-costs states-features-matrix)
 (map-vector
  (lambda (state-features-distribution)
   (lambda (features)
    (compute-features-cost features state-features-distribution)))
  states-features-matrix))

(define (boxes-movie->box-movies boxes-movie)
 (unless (= (length (remove-duplicates (map length boxes-movie))) 1)
  (panic "boxes movie has unequal #boxes per frame"))
 (transpose-list-of-lists boxes-movie))

;; Compute the newest feature
;; DEPRAVED use new-feature instead
(define (new-feature-reversed feature-type lookahead boxes-movie-so-far
			      new-boxes objects . quantized-flow-params)
 (let* ((box-movies
	 (boxes-movie->box-movies
	  (reverse
	   (pad-with-last-if-necessary
	    ;; Discard boxes before the previous frame to speed up computation
	    (cons new-boxes (take-if-possible (- lookahead 1) boxes-movie-so-far))
	    lookahead))))
	(params (if (not quantized-flow-params)
		    '()
		    (let* ((new-transformation (first quantized-flow-params))
			   (scale (second quantized-flow-params))
			   (v-d-o-medoids (third quantized-flow-params))
			   ;; since we will only keep the feature vector for the new frame
			   ;; the transformations for other frames (the previous ones) do not matter
			   (transformations-movie (map-n (const new-transformation)
							 (length (first box-movies)))))
		     (list transformations-movie scale v-d-o-medoids)))))
  (unless (= lookahead 2) (panic "New-feature-reversed: do not support lookahead larger than 2"))
  (last-vector
   (case (length new-boxes)
    ((1) (apply one-track-features
		`(,feature-type ,(first box-movies) ,lookahead #f ,objects
				#f ,@params)))
    ((2) (apply two-track-features
		`(,feature-type ,(first box-movies) ,(second box-movies) ,lookahead
				#f ,objects #f ,@params)))
    ((3) (apply three-track-features
		`(,feature-type ,(first box-movies) ,(second box-movies) ,(third box-movies)
				,lookahead #f ,objects #f ,@params)))
    (else (panic "feature-vector: unsupported no. of features"))))))

(define (map-all-tuples-list f l)
 (apply map-all-tuples f l))

(define (map-all-tuples f l . ls)
 (map f (all-values (nondeterministic-map a-member-of (cons l ls)))))

(define (pad-with-last-if-necessary l n)
 (if (and (not (null? l)) (< (length l) n))
     (let ((end (last l)))
      (append l (map-n (lambda (e) end) (- n (length l)))))
     l))

(define (model->psi-log hmm-model)
 (let ((psi (model->psi #f hmm-model)))
  (set-psi-a! psi (map-matrix log (psi-a psi)))
  (set-psi-b! psi (map-vector log (psi-b psi)))
  psi))

(define (initial-state-vector hmm) (psi-b (y hmm)))
(define (transition-matrix hmm) (psi-a (y hmm)))
(define (output-distribution hmm state)
 (vector-ref (psi-parameters (y hmm)) state))

(define (state-loglk-f g f hmms states boxes-movie new-boxes
		       lookahead objects . quantized-flow-params)
 (map-reduce + 0 (lambda (hmm state)
		  (let ((r (g (apply f `(,(x hmm) ,lookahead ,boxes-movie ,new-boxes ,objects
					 ,@quantized-flow-params))
			      (output-distribution hmm state))))
		   r))
             hmms states))
(define (state-loglk hmms states boxes-movie new-boxes
		     lookahead objects . quantized-flow-params)
 (apply state-loglk-f `(,compute-features-cost
			,new-feature-reversed
			,hmms ,states ,boxes-movie ,new-boxes
			,lookahead ,objects ,@quantized-flow-params)))

(define (initial-state-loglk hmm state)
 (+ (vector-ref (initial-state-vector hmm) state)
    (log (vector-length (initial-state-vector hmm)))))

(define (state-transition-loglk hmms states-movie new-states)
 (map-reduce + 0 (lambda (hmm state new-state)
                  (+ (matrix-ref (transition-matrix hmm)
				 state
				 new-state)
		     ;; This works for upper-triangular matrix
		     ;; It should be modified for full matrix
		     (log (- (vector-length (initial-state-vector hmm)) state))))
             hmms
             (first states-movie)
             new-states))

(define (a-set-of-boxes objects-movie-frame)
 (nondeterministic-map a-member-of objects-movie-frame))

(define-macro with-machine-type
 (lambda (form expander)
  (expander
   `(case ,(second form)
     ((fsm) ,(third form))
     ((hmm) ,(fourth form))
     (else (panic "Incorrect machine type!")))
   expander)))

(define (a-set-of-states state-machines type)
 (nondeterministic-map
  (lambda (machine)
   (an-integer-between
    0
    (- (vector-length ((with-machine-type type fsm-preconditions initial-state-vector) machine)) 1)))
  state-machines))

(define (voc4-detection->hashable-string v)
 (string-join
  " "
  (map number->string
       (list (voc4-detection-x1 v)
	     (voc4-detection-y1 v)
	     (voc4-detection-x2 v)
	     (voc4-detection-y2 v)
	     (voc4-detection-strength v)))))

(define-structure cached-box voc4 id)
(define (cached-box-cost b) (box-cost (cached-box-voc4 b)))

(define (ref/compute hash-table key function . args)
 (let ((v (hash-table-ref hash-table key #f)))
  (if v v (let ((a (apply function args)))
	   (%hash-table-add! (hash-table-entries hash-table)
			     (%hash-table-hash hash-table key)
			     key a)
	   a))))

(define (boxes-movie->cached-boxes-movie boxes-movie)
 (let loop ((r boxes-movie)
	    (n (vector->list
		(make-vector (length (first boxes-movie)) 0)))
	    (cbm '()))
  (if (null? r)
      (reverse cbm)
      (let ((v (map-n
		(lambda (i)
		 (map-indexed
		  (lambda (val j)
		   (make-cached-box val `(,i ,(+ j (list-ref n i)))))
		  (list-ref (car r) i)))
		(length n))))
       (loop (rest r)
	     (map + n (map length v))
	     (cons v cbm))))))

(define (cached-boxes-movie->boxes-movie cbm)
 (map (lambda (frame)
       (map (lambda (role)
	     (if (list? role)
		 (map cached-box-voc4 role)
		 (cached-box-voc4 role)))
	    frame))
      cbm))

(define (loglk-track-states hmm track states objects)
 (let ((hmm (vector
             (trained-hmm-feature-type hmm)
             (model->psi-log (trained-hmm-model hmm)))))
  (+ (initial-state-loglk hmm (first states))
     (reduce +
             (map-n
	      (lambda (n)
	       (compute-features-cost
		(new-feature-reversed
		 (x hmm)
		 2
		 (cdr (reverse (take (+ n 1) track)))
		 (last (take (+ n 1) track))
		 objects)
		(output-distribution hmm (list-ref states n))))
              (length track))
             0)
     (map-reduce
      + 0
      (lambda (state1 state2)
       (matrix-ref (transition-matrix hmm)
                   state1
                   state2))
      (but-last states)
      (cdr states)))))

(define (current-time-seconds) (car (gettimeofday)))

(define *profiling?* #t)
(define *profile-thunks* '())
(define (profile-f-thunk! f name)
 (let ((counter 0) (time 0))
  (set! *profile-thunks* (cons (lambda () (list name counter time)) *profile-thunks*))
  (lambda l
   (if *profiling?*
       (let* ((start (current-time-seconds))
              (r (apply f l))
              (end (current-time-seconds)))
        (set! counter (+ counter 1))
        (set! time (+ time (- end start)))
        r)
       (apply f l)))))
(define (reset-profiling!) (set! *profile-thunks* '()))
(define (show-profiles)
 (for-each
  (lambda (t) (let ((t (t)))
	  (format #t "~a ~a ~a ~a~%" (first t) (second t) (third t)
		  (if (= (second t) 0) 0 (/ (third t) (second t))))))
  *profile-thunks*))

(define (reselect-topn-boxes boxes-movies topn)
 (map
  (lambda (boxes-movie)
   (map
    (lambda (frame)
     (take-if-possible
      topn
      (map-concat
       (lambda (l) (sort l > voc4-detection-strength))
       (sort
	(transitive-equivalence-classesp
	 (lambda (a b) (equal? (voc4-detection-delta a) (voc4-detection-delta b)))
	 frame)
	<
	(lambda (a) (voc4-detection-delta (first a)))))))
    boxes-movie))
  boxes-movies))

(define (fill-sentence-tracker-roles boxes-movies model-names-groups roles)
 (let ((boxes-movie-object
	(reduce
	 (lambda (a b) (map append a b))
	 (map
	  second
	  (remove-if (lambda (e) (equal? (caar e) "person")) (zip model-names-groups boxes-movies)))
	 '()))
       (boxes-movie-thing
	(reduce
	 (lambda (a b) (map append a b))
	 (map second (zip model-names-groups boxes-movies))
	 '())))
  (format #t "Debugging:~%")
  (format #t "~a ~a~%" model-names-groups roles)
  (map
   (lambda (role)
    (cond ((equal? role "object") boxes-movie-object)
	  ((equal? role "thing") boxes-movie-thing)
	  (else (list-ref boxes-movies
			  (position-if (lambda (a) (member role a)) model-names-groups)))))
   roles)))

(define (minimum-with-position1 l)
 (let loop ((i 0) (r -1) (m #f) (l l))
  (if (null? l)
      (list m r)
      (if (<= (first l) (if m m infinity))
	  (loop (+ i 1) i (first l) (rest l))
	  (loop (+ i 1) r m (rest l))))))

(define (minimum-with-position1-f f l)
 (let loop ((i 0) (r -1) (m #f) (l l))
  (if (null? l)
      (list m r)
      (if (<= (f (first l)) (if m (f m) infinity))
	  (loop (+ i 1) i (first l) (rest l))
	  (loop (+ i 1) r m (rest l))))))

(define (fsm-check-transitions fsms states-movie states)
 (every (lambda (fsm old-state new-state)
         (matrix-ref (fsm-transitions fsm) old-state new-state))
        fsms (first states-movie) states))

(define (fsm-check-preconditions fsms states scale transformation new-boxes lookahead)
 (every (lambda (fsm state)
         (apply (vector-ref (fsm-preconditions fsm) state)
		scale
		transformation
		(if (list? (first new-boxes)) new-boxes (map list new-boxes))))
        fsms states))

(define (viterbi-sentence-track-in-c sentence-machines n-tracks roles boxes-movie
				     flow-movie scale objects feature-medoids final-state?)
 (let ((flow-movie (pad-with-last-if-necessary flow-movie (length boxes-movie)))
       (sentence (map trained-hmm-model sentence-machines)))
  (unless (every (lambda (boxes) (= (length (first boxes-movie))
				    (length boxes)))
		 boxes-movie)
   (panic "viterbi-sentence-track-in-c: boxes-movie with unequal lengths"))
  ;; Call tracking function in C here
  (let* ((sentence (with-c-pointers
		    (lambda (s-array)
		     (initialize-sentence (length sentence) s-array))
		    (list->vector sentence)))
	 (roles (map (lambda (r)
		      (let ((r-vec (allocate-ivec (length r))))
		       (for-each-indexed (lambda (x i) (set-ivec! r-vec i x)) r)
		       r-vec))
		     roles))
	 (c-roles (let ((array (malloc (* (length roles) c-sizeof-s2cuint))))
		   (list->c-array array roles c-s2cuint-set! c-sizeof-s2cuint)))
	 (c-flow-struct-movie (map (lambda (fl) (optical-flow->c-flow-struct fl)) flow-movie))
	 (c-feature-medoids (map (lambda (fm) (feature-medoid->c-feature-medoid fm)) feature-medoids))
	 (c-boxes-movie (map (lambda (boxes-per-frame)
			      (map voc4-detection->box boxes-per-frame))
			     boxes-movie))
	 (c-boxes-movie-struct (allocate-boxes-movie (length c-boxes-movie)
						     (length (first c-boxes-movie))))
	 (_ (for-each-indexed
	     (lambda (boxes-per-frame t)
	      (for-each-indexed
	       (lambda (box i)
		(set-boxes-movie! c-boxes-movie-struct t i box))
	       boxes-per-frame))
	     c-boxes-movie))
	 (box-sequence-c (list->c-exact-array (malloc (* c-sizeof-int (length boxes-movie)))
					      (map-n (const 0) (length boxes-movie))
					      c-sizeof-int #t))
	 (result
	  (time-code
	   (with-c-pointers
	    (lambda (c-flow-struct-array)
	     (with-c-strings
	      (lambda (objects-array)
	       (with-c-pointers
		(lambda (c-feature-medoids-array)
		 (sentence-maximum-one
		  sentence n-tracks c-roles c-boxes-movie-struct c-flow-struct-array
		  (length flow-movie) scale objects-array (length objects)
		  c-feature-medoids-array (length feature-medoids) model-constraint:none
		  (if final-state? 1 0) box-sequence-c))
		(list->vector c-feature-medoids)))
	      (list->vector objects)))
	    (list->vector c-flow-struct-movie))))
	 (box-sequence (c-exact-array->list box-sequence-c c-sizeof-int (length boxes-movie) #t))
	 (result (if (= minus-infinity result)
		     #f
		     (list (map (lambda (state boxes)
				 (let ((j state)
				       (ii (length boxes)))
				  (map-n (lambda _
					  (let ((box (list-ref boxes (modulo j ii))))
					   (set! j (quotient j ii))
					   box))
					 n-tracks)))
				box-sequence boxes-movie)
			   result))))
   (free-sentence sentence)
   (free c-roles)
   (for-each free-ivec roles)
   (for-each free-c-flow-struct c-flow-struct-movie)
   (for-each free-feature-medoid c-feature-medoids)
   (for-each (lambda (boxes) (for-each free-box boxes)) c-boxes-movie)
   (free-boxes-movie c-boxes-movie-struct)
   (free box-sequence-c)
   result)))

(define (viterbi-fast-sentence-track-one
	 objects-movie transformations-movie
	 alpha beta kappa scale machines machine-type lookahead
	 medoids objects v-d-o-medoids . beam-width)
 (define (eliminate-infinities costs)
  (remove-if (lambda (l) (= (first (third l)) infinity)) costs))
 (define (beam boxes-states-costs)
  (if (null? beam-width)
      boxes-states-costs
      (begin
       (format #t "Beam the sequences. Before: ~a After: ~a ~%"
	       (length boxes-states-costs)
	       (if (> (car beam-width) (length boxes-states-costs))
		   (length boxes-states-costs)
		   (car beam-width)))
       (take-if-possible (car beam-width) (sort boxes-states-costs < (o first third))))))
 (format #t "Boxes space A: ~a~%" (length (all-values (list (a-set-of-boxes (first objects-movie))))))
 (unless (and (>= (length objects-movie) 2) (= (length objects-movie) (+ (length transformations-movie) 1)))
  (panic "viterbi-sentence-track 1"))
 (unless (or (and (not machines) (not lookahead) (not objects) (not v-d-o-medoids))
	    (and machines lookahead objects v-d-o-medoids))
  (format #t "~a ~a ~a ~a ~%" machines lookahead objects v-d-o-medoids)
  (panic "viterbi-sentence-track 2"))
 (let* ((machines
	 (with-machine-type
	  machine-type
	  machines
	  (map (lambda (machine) `#(,(trained-hmm-feature-type machine)
			       ,(model->psi-log (trained-hmm-model machine))))
	       machines)))
	(objects-movie (boxes-movie->cached-boxes-movie objects-movie))
	(transformations-movie-backup
	 (pad-with-last-if-necessary transformations-movie (length objects-movie)))
        (boxes-movies-state-movies-and-costs
         (beam
          (eliminate-infinities
           (all-values
	    (let ((boxes (a-set-of-boxes (first objects-movie)))
		  (states(case machine-type
			  ((fsm) (map (const 0) machines))
			  ((hmm) (a-set-of-states machines machine-type))
			  (else (fuck-up)))))
	     (list (list boxes)
		   (list states)
		   (list
		    (+ (* alpha (- (map-reduce + 0 cached-box-cost boxes)))
		       (if (zero? beta)
			   0
			   (with-machine-type
			    machine-type
			    (if (fsm-check-preconditions
				 machines states scale '(()) (map cached-box-voc4 boxes) lookahead)
				0
				infinity)
			    (* (- beta)
			       (+ (map-reduce + 0 initial-state-loglk machines states)
				  (state-loglk machines states '() (map cached-box-voc4 boxes)
					       lookahead objects (first transformations-movie-backup)
					       scale v-d-o-medoids))))))
		    (* alpha (- (map-reduce + 0 cached-box-cost boxes)))
		    0))))))))
  (let loop ((frame 1)
	     (boxes-movies (map first boxes-movies-state-movies-and-costs))
	     (states-movies (map second boxes-movies-state-movies-and-costs))
	     (costs (map third boxes-movies-state-movies-and-costs))
	     (transformations-movie transformations-movie-backup)
	     (objects-movie (rest objects-movie)))
   (format #t "frame: ~a~%" frame)
   (format #t "viterbi-sentence-track: ~s~%" (length objects-movie))
   (cond
    ((null? costs)
     (format #t "viterbi-sentence-track, all costs were infinity~%")
     #f)
    ((or (null? objects-movie) (null? transformations-movie))
     (let ((b-s-c
            (unzip
	     ;; only non-initial state is accepting
	     (remove-if-not
	      (lambda (b-s-c)
	       (every
		(lambda (machine s)
		 (= (vector-length
		     ((with-machine-type machine-type fsm-preconditions initial-state-vector) machine))
		    (+ s 1)))
		machines
		(first (second b-s-c))))
	      (zip boxes-movies states-movies costs)))))
      (if (null? b-s-c)
	  (begin
	   (format #t "No final state has been accepted~%")
	   #f)
	  (let* ((boxes-movies (first b-s-c))
		 (states-movies (second b-s-c))
		 (costs (third b-s-c))
		 (best-with-position (minimum-with-position1-f first costs))
		 (boxes-movie (cached-boxes-movie->boxes-movie
			       (reverse (list-ref boxes-movies (second best-with-position)))))
		 (box-movies (boxes-movie->box-movies boxes-movie)))
	   (list boxes-movie
		 (reverse (list-ref states-movies (second best-with-position)))
		 ;; not normalized to #frames
		 ;; (first best-with-position) ; (list joint per-frame between-frame)
		 (let ((c (first best-with-position)))
		  (format #t "raw: ~a~%#roles: ~a~%#length:~a~%" (first c) (length box-movies) frame)
		  `(,(/ (first c) (* (length box-movies) frame)) ,@(rest c)))
		 '#())))))
    (else
     (format #t "Boxes space: ~a~%" (length (all-values (list (a-set-of-boxes (first objects-movie))))))
     (format #t "HMMs space: ~a~%" (length (all-values (list (a-set-of-states machines machine-type)))))
     (format #t "Search space: ~a~%"
             (length (all-values (list (a-set-of-boxes (first objects-movie))
				       (a-set-of-states machines machine-type)))))
     (let*
       ((previous-transformation (first transformations-movie))
	(new-transformation (second transformations-movie))
	(mem-model-similarity
         (let ((h (create-hash-table)))
          (lambda (b1 b2)
           (ref/compute h `(,(cached-box-id b1) ,(cached-box-id b2))
                        (lambda (b1 b2)
			 (if (or (equal? (voc4-detection-model b1) "padding")
				(equal? (voc4-detection-model b2) "padding")
				(equal? (voc4-detection-model b1) (voc4-detection-model b2)))
			     0
			     infinity))
                        (cached-box-voc4 b1)
                        (cached-box-voc4 b2)))))
	(mem-box-pair-cost
         (let ((h (create-hash-table))
               (g (create-hash-table)))
          (lambda (b1 b2 transformation s)
           (ref/compute g `(,(cached-box-id b1) ,(cached-box-id b2))
			box-distance
                        (ref/compute h (cached-box-id b1)
                                     forward-project-box-scaled
                                     (cached-box-voc4 b1)
                                     transformation
                                     0
                                     s)
                        (cached-box-voc4 b2)))))
	(mem-one-track-features
         (let ((h (create-hash-table)))
          (lambda (type box-movie lk d objs discard? . flow-params)
           (apply ref/compute `(,h (,type ,(map cached-box-id  box-movie))
				   ,one-track-features
				   ,type ,(map cached-box-voc4 box-movie) ,lk ,d
				   ,objs ,discard? ,@flow-params)))))
	(mem-pairwise-track-features
         (let ((h (create-hash-table)))
          (lambda (track1 track2 lookahead)
           (ref/compute h `(,(map cached-box-id track1)
                            ,(map cached-box-id track2))
                        pairwise-track-features
                        (map cached-box-voc4 track1)
                        (map cached-box-voc4 track2)
                        lookahead))))
	(mem-new-feature-reversed
	 (lambda (type lk boxes-movie boxes objs . flow-params)
          (let* ((box-movies
		  (boxes-movie->box-movies
		   (reverse
		    (pad-with-last-if-necessary
		     ;; Discard boxes in the middle of the movie to speed up computation
		     (cons
		      boxes
		      (append (take-if-possible lk boxes-movie)
			      (if (<= (length boxes-movie) lk) '() (list (last boxes-movie)))))
		     lk))))
		 (params
		  (if flow-params
		      (if (= 3 (length flow-params))
			  (list (map-n (const (first flow-params)) (length (first box-movies)))
				(second flow-params)
				(third flow-params))
			  (panic "mem-new-feature-reversed"))
		      '())))
           (last-vector
	    (case (length boxes)
	     ((1) (apply mem-one-track-features `(,type ,(first box-movies) ,lk #f ,objs #f ,@params)))
	     ((2) (apply two-track-features-f `(,mem-one-track-features
						,mem-pairwise-track-features
						,type ,(first box-movies)
						,(second box-movies) ,lk
						#f ,objs #f ,@params)))
	     ((3) (apply three-track-features-f `(,mem-one-track-features
						  ,mem-pairwise-track-features
						  ,type ,(first box-movies)
						  ,(second box-movies)
						  ,(third box-movies)
						  ,lk #f ,objs #f ,@params)))
	     (else (fuck-up)))))))
	(untenable-cost (list infinity infinity infinity))
	(new-boxes-movies-state-movies-and-costs
	 (beam
	  (eliminate-infinities
	   (time-code
	    (all-values
             (let*
	       ((new-boxes (a-set-of-boxes (first objects-movie)))
		(new-states (a-set-of-states machines machine-type))
		(new-costs
		 (map
		  (lambda (boxes-movie states-movie cost)
		   (if (= (map-reduce + 0 mem-model-similarity (first boxes-movie) new-boxes) infinity)
		       untenable-cost
		       (let ((state-machine-cost
			      (if (or (not machines) (zero? beta))
				  0
				  (with-machine-type
				   machine-type
				   (if (and (fsm-check-transitions machines states-movie new-states)
					  (fsm-check-preconditions
					   machines new-states scale
					   (cdr transformations-movie)
					   ;; needs previous frame for adverbs
					   (zip (map cached-box-voc4 new-boxes)
						(map cached-box-voc4 (first boxes-movie)))
					   lookahead))
				       0
				       infinity)
				   (+ (state-transition-loglk machines states-movie new-states)
				      (state-loglk-f compute-features-cost
						     mem-new-feature-reversed
						     machines new-states
						     boxes-movie
						     new-boxes
						     lookahead objects
						     new-transformation
						     scale v-d-o-medoids)))))
			     (per-frame-cost (map-reduce + 0 cached-box-cost new-boxes))
			     (between-frame-cost
			      (map-reduce
			       +
			       0
			       (lambda (b1 b2) (mem-box-pair-cost b1 b2 previous-transformation scale))
			       (first boxes-movie)
			       new-boxes)))
			(if (= state-machine-cost infinity)
			    untenable-cost
			    ;; not normalized to #roles
			    (list (+ (* beta (- state-machine-cost))
				     between-frame-cost
				     (* alpha (- per-frame-cost))
				     (first cost))
				  (+ (* alpha (- per-frame-cost)) (second cost))
				  (+ between-frame-cost (third cost)))))))
		  boxes-movies states-movies costs))
		(best-cost-and-position (minimum-with-position1-f first new-costs)))
              (list
               (cons new-boxes (list-ref boxes-movies (second best-cost-and-position)))
               (cons new-states (list-ref states-movies (second best-cost-and-position)))
               (first best-cost-and-position)))))))))
      (format #t "Actual: ~a~%" (length new-boxes-movies-state-movies-and-costs))
      (loop (+ frame 1)
            (map first new-boxes-movies-state-movies-and-costs)
            (map second new-boxes-movies-state-movies-and-costs)
            (map third new-boxes-movies-state-movies-and-costs)
            (rest transformations-movie)
	    (rest objects-movie))))))))

(define (viterbi-event-track-one objects-movie transformations-movie
				 alpha beta kappa scale machine machine-type
				 lookahead medoids objects v-d-o-medoids)
 (when (list? machine) (panic "The event tracker only accepts one HMM"))
 (viterbi-fast-sentence-track-one
  objects-movie transformations-movie
  alpha beta kappa scale (list machine) machine-type
  lookahead medoids objects v-d-o-medoids))

(define (viterbi-track-one boxes-movie transformation-movie alpha beta kappa _1 _2 scale)
 (map
  first
  (first
   (viterbi-fast-sentence-track-one
    (map list
	 (map
	  (lambda (boxes)
	   (if (null? boxes)
	       ;; The strength of this padding box is 0 intentionally,
	       ;; otherwise any tracks with padding boxes will have cost -infinity
	       (list (make-voc4-detection -1 -1 -1 -1 '() -1 0 0 0 "padding"))
	       boxes))
	  boxes-movie))
    transformation-movie alpha 0 kappa scale '() 'hmm #f #f #f #f)))) ;; either 'hmm or 'fsm is OK

(define (some-valid-box? boxes threshold)
 (some (lambda (box) (>= (voc4-detection-strength box) threshold)) boxes))

(define (keep-track-in-context? box-movie box-movies)
 (define (soft-same-track? track1 track2)
  ;; TODO siddharth: move hardwired constants from here
  (>= (count-if
      (lambda (e) (<= (distance (box->vector (first e)) (box->vector (second e)))
		20))
      (zip track1 track2))
     (* 0.9 (length track1))))
 (or (null? box-movies)
    (not (some (lambda (old-box-movie) (soft-same-track? box-movie old-box-movie))
	       box-movies))))

(define (viterbi-track-multiple boxes-movie transformation-movie
				alpha beta kappa subverted-model-threshold dt?
				minimum-track-length
				maximum-track-overlap-ratio
				overgeneration-minimum-track-length
				overgeneration-maximum-track-overlap-ratio
				suppression-delta scale
				max-tracks)
 (let loop ((boxes-movie boxes-movie) (box-movies '()) (i 0))
  (if (some (lambda (boxes) (some-valid-box? boxes subverted-model-threshold))
	    boxes-movie)
      (let ((box-movie (viterbi-track-one
			boxes-movie transformation-movie
			alpha beta kappa subverted-model-threshold dt?
                        scale)))
       (dtrace "length of box-movie:" (length box-movie))
       (dtrace "length of boxes-movie:" (length boxes-movie))
       (if (>= i max-tracks)
	   (begin
	    (unless *quiet-mode?*
	     (format #t "Terminating: maximum of ~a iterations reached~%" i)
	     (format #t "Total Tracks: ~a~%" (length box-movies)))
	    (list (length box-movies) (reverse box-movies)))
	   (loop
	    (map2 (lambda (box boxes)
		   (if (null? boxes)
		       '()
		       (map
			(lambda (box2)
			 (if (or
			      (some (lambda (c) (point-in-voc4-detection? c box))
				    (voc4-detection->corners box2))
			      (some (lambda (c) (point-in-voc4-detection? c box2))
				    (voc4-detection->corners box)))
			     (update-voc4-strength
			      box2
			      (+ (voc4-detection-strength box2)
				 (* suppression-delta
				    (voc4-detection-intersection-divided-by-union
				     box box2))))
			     box2))
			boxes)))
		  box-movie boxes-movie)
	    (if (keep-track-in-context? box-movie box-movies)
	    	(cons box-movie box-movies)
	    	(begin
	    	 (unless *quiet-mode?*
	    	  (format #t "Skipping track, overlaps too much.. maybe~%"))
	    	 box-movies))
	    (+ i 1))))
      (begin (format #t "Terminating: no more valid boxes~%")
	     (format #t "Total Tracks: ~a~%" (length box-movies))
	     (list (length box-movies) (reverse box-movies))))))

(define (get-cleaned-up-model-names-list video)
 (map
  (lambda (model-class)
   (remove-if (lambda (e) (or (suffix? "wheelbarrow" e) (suffix? "crawl" e)))
	      model-class))
  (get-model-names-list video)))

(define (viterbi-prepare-tracks scale size video-length klt-movie
				detector-boxes-movie-model-name-pairs top-n
				model-path
				model-threshold-tracker-offset
				profile-best-boxes?
				look-ahead)
 ;; returns a boxes-movie
 (let* ((detector-boxes-movies
         (map (lambda (detector-boxes-movie)
               (map (lambda (detector-boxes)
                     (sublist (sort detector-boxes > voc4-detection-strength)
                              0
                              (min (length detector-boxes) top-n)))
                    detector-boxes-movie))
              (map first detector-boxes-movie-model-name-pairs)))
        (model-names (map second detector-boxes-movie-model-name-pairs))
        (model-name (first model-names))
        (model-thresholds (map (lambda (model-name)
                                (model-threshold model-name model-path))
                               model-names))
        ;; The thresholds, max-threshold, and delta are computed after the
        ;; first top-n filter but before the second top-n filter.
        (thresholds
         (map (lambda (detector-boxes-movie model-threshold)
               (min (matlab-threshold-otsu
                     (if profile-best-boxes?
                         (remove minus-infinity
                                 (map (lambda (detector-boxes)
                                       (map-reduce max
                                                   minus-infinity
                                                   voc4-detection-strength
                                                   detector-boxes))
                                      detector-boxes-movie))
                         (map-reduce append
                                     '()
                                     (lambda (a) (map voc4-detection-strength a))
                                     detector-boxes-movie)))
                    (+ model-threshold model-threshold-tracker-offset)))
              detector-boxes-movies
              model-thresholds))
        (max-threshold (reduce max thresholds minus-infinity))
        (detector-boxes-movies
         (map
          (lambda (detector-boxes-movie threshold)
           (let ((delta (- max-threshold threshold)))
            (map (lambda (detector-boxes)
                  (map (lambda (detector-box)
                        (update-voc4-strength
                         detector-box
                         (+ (voc4-detection-strength detector-box) delta)))
                       detector-boxes))
                 detector-boxes-movie)))
          detector-boxes-movies thresholds))
        (detector-boxes-movie
         (map-n
	  (lambda (i)
	   (let ((detector-boxes
		  (sort (map-reduce append
				    '()
				    (lambda (detector-boxes-movie)
				     (list-ref detector-boxes-movie i))
				    detector-boxes-movies)
			>
			voc4-detection-strength)))
	    (if top-n
		(sublist detector-boxes 0 (min (length detector-boxes) top-n))
		detector-boxes)))
          (length (first detector-boxes-movies))))
        (predicted-boxes-movie
         (predict-boxes (min look-ahead video-length)
                        detector-boxes-movie
                        klt-movie scale))
	(w (/ (x size) scale))
	(h (/ (y size) scale)))
  (list (map
	 (lambda (bs)
	  (remove-if-not
	   (lambda (box) (let ((c (voc4-detection-center box))) (and (<= 0 (x c) w) (<= 0 (y c) h))))
	   bs))
	 predicted-boxes-movie)
        max-threshold)))

(define (viterbi-track scale size video-length klt-movie optical-flow-movie
		       detector-boxes-movie-model-name-pairs top-n with-dt?
		       model-path
		       model-threshold-tracker-offset
		       profile-best-boxes?
		       look-ahead
		       minimum-track-length
		       maximum-track-overlap-ratio
		       overgeneration-minimum-track-length
		       overgeneration-maximum-track-overlap-ratio
		       suppression-delta
		       alpha
		       beta
		       kappa
		       max-tracks)
 (let* ((boxes-movie-and-threshold
         (viterbi-prepare-tracks scale size video-length klt-movie
                                 detector-boxes-movie-model-name-pairs top-n
                                 model-path
                                 model-threshold-tracker-offset
                                 profile-best-boxes?
                                 look-ahead))
        (count-and-box-movies
         (viterbi-track-multiple
          (first boxes-movie-and-threshold)
          optical-flow-movie alpha beta kappa (second boxes-movie-and-threshold) with-dt?
          minimum-track-length
          maximum-track-overlap-ratio
          overgeneration-minimum-track-length
          overgeneration-maximum-track-overlap-ratio
          suppression-delta scale max-tracks))
        (number-of-non-overgenerated-tracks (first count-and-box-movies))
        (box-movies (second count-and-box-movies))
        (smooth-box-movies
         (map
          (lambda (box-movie)
           (let* ((l (length (remove-if-not nondropped-box? box-movie)))
                  (pieces (max 5 (exact-round (* (/ l 140) 10)))))
            (smooth-box-movie box-movie pieces pieces pieces pieces)))
          box-movies)))
  (if number-of-non-overgenerated-tracks
      (vector (first boxes-movie-and-threshold)
	      (sublist box-movies 0 number-of-non-overgenerated-tracks)
	      (sublist smooth-box-movies 0 number-of-non-overgenerated-tracks)
	      box-movies
	      smooth-box-movies)
      #f)))

(define (write-viterbi-track-results video model-name viterbi-track-results)
 (let ((predicted-boxes-movie (vector-ref viterbi-track-results 0))
       (box-movies (vector-ref viterbi-track-results 1))
       (smooth-box-movies (vector-ref viterbi-track-results 2))
       (overgenerated-box-movies (vector-ref viterbi-track-results 3))
       (overgenerated-smooth-box-movies (vector-ref viterbi-track-results 4)))
  (write-voc4-predicted-boxes-movie predicted-boxes-movie video model-name)
  ;; box-movies
  (for-each-indexed
   (lambda (box-movie i)
    (write-voc4-tracked-box-movie
     box-movie video model-name (number->string (+ i 1))))
   box-movies)
  ;; smooth-box-movies
  (for-each-indexed
   (lambda (box-movie i)
    (write-voc4-smooth-tracked-box-movie
     box-movie video model-name (number->string (+ i 1))))
   smooth-box-movies)
  ;; overgenerated-box-movies
  (for-each-indexed
   (lambda (box-movie i)
    (write-voc4-overgenerated-tracked-box-movie
     box-movie video model-name (number->string (+ i 1))))
   overgenerated-box-movies)
  ;; overgenerated-smooth-box-movies
  (for-each-indexed
   (lambda (box-movie i)
    (write-voc4-overgenerated-smooth-tracked-box-movie
     box-movie video model-name (number->string (+ i 1))))
   overgenerated-smooth-box-movies)))

(define (viterbi-track-group-in-memory
	 video klt-movie optical-flow-movie
	 model-group detector-boxes-movies model-names
	 nms alpha beta kappa top-n with-dt? model-path model-threshold-tracker-offset
	 profile-best-boxes?
	 look-ahead
	 minimum-track-length
	 maximum-track-overlap-ratio
	 overgeneration-minimum-track-length
	 overgeneration-maximum-track-overlap-ratio
	 suppression-delta max-tracks)
 (viterbi-track
  ;; TODO This might fail if no detections exist for a model
  (video-scale video)
  (video-dimensions video)
  (- (video-last-frame video) (video-first-frame video))
  klt-movie optical-flow-movie
  (map (lambda (model-name)
        (find-if (lambda (x) (equal? model-name (second x)))
                 (zip detector-boxes-movies model-names)))
       model-group)
  top-n with-dt? model-path
  model-threshold-tracker-offset
  profile-best-boxes?
  look-ahead
  minimum-track-length
  maximum-track-overlap-ratio
  overgeneration-minimum-track-length
  overgeneration-maximum-track-overlap-ratio
  suppression-delta
  alpha beta kappa
  max-tracks))

;;; Sentences

(define *class->noun*
 '(("bag" "bag")
   ("baseball-bat" "bat")
   ("bench" "bench")
   ("bicycle" "bicycle")
   ("big-ball" "ball")
   ("bucket" "bucket")
   ("cage" "cage")
   ("cardboard-box" "box")
   ("car" "car")
   ("cart" "cart")
   ("chair" "chair")
   ("closet" "closet")
   ("dog" "dog")
   ("door" "door")
   ("garbage-can" "can")
   ("golf-club" "club")
   ("ladder" "ladder")
   ("mailbox" "mailbox")
   ("microwave" "microwave")
   ("motorcycle" "motorcycle")
   ("person-crouch" "person")
   ("person-down" "person")
   ("person" "person")
   ("pogo-stick" "pogo-stick")
   ("rake" "rake")
   ("shovel" "shovel")
   ("skateboard" "skateboard")
   ("small-ball" "ball")
   ("suv" "SUV")
   ("table" "table")
   ("toy-truck" "truck")
   ("trailer" "trailer")
   ("trash-bag" "bag")
   ("tripod" "tripod")
   ("truck" "truck")
   ("gun" "gun")
   ("ball" "ball")
   ("sign" "sign")
   ("giraffe" "giraffe")))

(define *class->restrictive-adjective*
 '(("baseball-bat" "baseball")
   ("cardboard-box" "cardboard")
   ("garbage-can" "garbage")
   ("golf-club" "golf")
   ("toy-truck" "toy")
   ("trash-bag" "trash")))

(define *class->size-adjective*
 '(("big-ball" "big")
   ("small-ball" "small")))

(define *class->size-adjective-bounds*
 '(("bag" (0.007 0.016))
   ;; ("baseball-bat" ())
   ;; ("bench" ())
   ("bicycle" (0.02 0.03))
   ;; ("bucket" ())
   ;; ("cage" ())
   ("cardboard-box" (0.009 0.016))
   ("car" (0.1 0.2))
   ;; ("cart" ())
   ;; ("chair" ())
   ;; ("closet" ())
   ("dog" (0.007 0.02))
   ("door" (0.016 0.05))
   ;; ("garbage-can" ())
   ;; ("golf-club" ())
   ;; ("ladder" ())
   ;; ("mailbox" ())
   ;; ("microwave" ())
   ("motorcycle" (0.08 0.18))
   ("person" (0.1 0.18))
   ;; ("pogo-stick" ())
   ;; ("rake" ())
   ;; ("shovel" ())
   ("skateboard" (0.007 0.014))
   ("suv" (0.2 0.5))
   ("table" (0.15 0.22))
   ("toy-truck" (0.1 0.15))
   ;; ("trailer" ())
   ;; ("trash-bag" ())
   ;; ("tripod" ())
   ("truck" (0.2 0.53))
   ("gun" (0.2 0.5))
   ("ball" (0.009 0.016))
   ("sign" (0.009 0.016))
   ("giraffe" (0.009 0.016))))

(define *class->shape-adjective-bound*
 '(("bag" 0.89)
   ;; ("baseball-bat" ())
   ;; ("bench" ())
   ("bicycle" 1.47)
   ;; ("bucket" ())
   ;; ("cage" ())
   ("cardboard-box" 0.97)
   ("car" 1.48)
   ;; ("cart" ())
   ;; ("chair" ())
   ;; ("closet" ())
   ("dog" 1.12)
   ("door" 0.42)
   ;; ("garbage-can" ())
   ;; ("golf-club" ())
   ;; ("ladder" ())
   ;; ("mailbox" ())
   ;; ("microwave" ())
   ("motorcycle" 1.43)
   ("person" 0.71)
   ;; ("pogo-stick" ())
   ;; ("rake" ())
   ;; ("shovel" ())
   ("skateboard" 2.11)
   ("suv" 1.47)
   ("table" 1.23)
   ("toy-truck" 1.08)
   ;; ("trailer" ())
   ;; ("trash-bag" ())
   ;; ("tripod" ())
   ("truck" 1.47)
   ("gun" 1.47)
   ("ball" 0.97)
   ("sign" 0.97)
   ("giraffe" 0.97)))

(define *verbs*
 '(("approach" "approached" required preverbal-adverb exogenous-motion-pp)
   ("arrive" "arrived" not-allowed postverbal-adverb exogenous-motion-pp)
   ("attach" "attached" required (before "an" "object" "to")
    (default "themselves") preverbal-adverb)
   ("bounce" "bounced" not-allowed postverbal-adverb endogenous-motion-pp)
   ("bury" "buried" required)
   ("carry" "carried" required preverbal-adverb endogenous-motion-pp)
   ("catch" "caught" required exogenous-motion-pp)
   ("chase" "chased" required preverbal-adverb endogenous-motion-pp)
   ("close" "closed" required)
   ("collide" "collided" required (before "with") preverbal-adverb
    exogenous-motion-pp)
   ("dig" "digging" optional (before "with") (restriction "rake" "shovel") aux)
   ("drop" "dropped" required)
   ("enter" "entered" required
    (conditional-default "something" "car" "suv" "truck" "door")
    preverbal-adverb endogenous-motion-pp)
   ("exchange" "exchanged" required (before "an" "object" "with")
    preverbal-adverb)
   ("exit" "exited" required
    (conditional-default "something" "car" "suv" "truck" "door")
    preverbal-adverb endogenous-motion-pp)
   ("fall" "fell" optional (before "because" "of") postverbal-adverb
    endogenous-motion-pp)
   ("flee" "fled" optional (before "from") postverbal-adverb
    endogenous-motion-pp)
   ("fly" "flew" not-allowed postverbal-adverb endogenous-motion-pp)
   ("follow" "followed" required preverbal-adverb endogenous-motion-pp)
   ("get" "got" required (before "an" "object" "from"))
   ("give" "gave" required (before "an" "object" "to"))
   ("go" "went" not-allowed (before "away") postverbal-adverb
    endogenous-motion-pp)
   ("hand" "handed" required (after "an" "object"))
   ("haul" "hauled" required preverbal-adverb endogenous-motion-pp)
   ("have" "had" required)
   ("hit" "hit" required
    (conditional-before ("something" "with") ("golf-club" "baseball-bat")))
   ("hold" "held" required)
   ("jump" "jumped" optional (before "over") postverbal-adverb
    endogenous-motion-pp)
   ("kick" "kicked" required preverbal-adverb endogenous-motion-pp)
   ("leave" "left" not-allowed postverbal-adverb endogenous-motion-pp)
   ("lift" "lifted" required preverbal-adverb)
   ("move" "moved" required (default "itself") preverbal-adverb
    endogenous-motion-pp)
   ("open" "opened" required)
   ("pass" "passed" required preverbal-adverb exogenous-motion-pp)
   ("pick up" "picked" required (after "up"))
   ("push" "pushed" required preverbal-adverb endogenous-motion-pp)
   ("put down" "put" required (after "down"))
   ("raise" "raised" required (default "themselves"))
   ("receive" "received" required
    (conditional-before ("an" "object" "from") ("person" "mailbox")))
   ("replace" "replaced" required preverbal-adverb)
   ("run" "ran" optional (before "to") postverbal-adverb endogenous-motion-pp)
   ("snatch" "snatched" required (before "an" "object" "from")
    preverbal-adverb)
   ("stop" "stopped" optional preverbal-adverb)
   ("take" "took" required (before "an" "object" "from") preverbal-adverb)
   ("throw" "threw" required preverbal-adverb endogenous-motion-pp)
   ("touch" "touched" required)
   ("turn" "turned" not-allowed endogenous-motion-pp)
   ("walk" "walked" optional (before "to") postverbal-adverb
    endogenous-motion-pp)))

(define *prepositions*
 '((right "to" "the" "left" "of")
   (above-right "below" "and" "to" "the" "left" "of")
   (above "below")
   (above-left "below" "and" "to" "the" "right" "of")
   (left "to" "the" "right" "of")
   (below-left "above" "and" "to" "the" "right" "of")
   (below "above")
   (below-right "above" "and" "to" "the" "left" "of")))

(define *endogenous-motion-pps*
 '((none)
   (right "rightward")
   (above-right "rightward" "and" "upward")
   (above "upward")
   (above-left "leftward" "and" "upward")
   (left "leftward")
   (below-left "leftward" "and" "downward")
   (below "downward")
   (below-right "rightward" "and" "downward")))

(define *exogenous-motion-pps*
 '((none)
   (right "from" "the" "left")
   (above-right "from" "below" "and" "to" "the" "left")
   (above "from" "below")
   (above-left "from" "below" "and" "to" "the" "right")
   (left "from" "the" "right")
   (below-left "from" "above" "and" "to" "the" "right")
   (below "from" "above")
   (below-right "from" "above" "and" "to" "the" "left")))

(define *minimum-velocity* 3)

(define *motion-threshold* 70)

(define (hyphens->spaces string)
 (list->string (map (lambda (char) (if (char=? char #\-) #\space char))
		    (string->list string))))

(define (angle->direction a)
 (cond ((<= (- (/ half-pi 4)) a (/ half-pi 4)) 'right)
       ((<= (/ half-pi 4) a (* 3 (/ half-pi 4))) 'above-right)
       ((<= (* 3 (/ half-pi 4)) a (* 5 (/ half-pi 4))) 'above)
       ((<= (* 5 (/ half-pi 4)) a (* 7 (/ half-pi 4))) 'above-left)
       ((or (<= (- pi) a (* 7 (- (/ half-pi 4))))
	    (<= (* 7 (/ half-pi 4)) a pi))
	'left)
       ((<= (* 3 (- (/ half-pi 4))) a (- (/ half-pi 4))) 'below-left)
       ((<= (* 5 (- (/ half-pi 4))) a (* 3 (- (/ half-pi 4)))) 'below)
       ((<= (* 7 (- (/ half-pi 4))) a (* 5 (- (/ half-pi 4)))) 'below-right)
       (else (fuck-up))))

(define (overall-distance track)
 (distance (voc4-detection-center (last track))
	   (voc4-detection-center (first track))))

(define (track-motion track)
 (let ((velocity-vectors
	(remove-if-vector
	 (lambda (velocity-vector)
	  (< (vector-ref velocity-vector 0) *minimum-velocity*))
	 (map-vector (lambda (feature-vector)
		      (vector
		       ;; magnitude
		       (vector-ref feature-vector 4)
		       ;; direction
		       (vector-ref feature-vector 5)))
		     (one-track-features 'no-pose track 2 #f #f #t)))))
  (if (zero? (vector-length velocity-vectors))
      '#(0 0)
      (k*v (/ (vector-length velocity-vectors))
	   (map-reduce-vector
	    v+ '#(0 0) polar->rect velocity-vectors)))))

(define (generate-adverb track)
 (if track
     (let ((velocity-vector (track-motion track)))
      ;; hardcoded values given profiling 14Mar2011
      (cond ((<= *minimum-velocity*
		(magnitude velocity-vector)
		(+ *minimum-velocity* 2))
	     "slowly")
	    ((> (magnitude velocity-vector) (+ *minimum-velocity* 4))
	     "quickly")
	    (else '())))
     '()))

(define (generate-direction track)
 (if track
     (let* ((velocity-vector (track-motion track)))
      (if (< (magnitude velocity-vector) *minimum-velocity*)
	  'none
	  (angle->direction (orientation velocity-vector))))
     'none))

(define (get-before lexical-entry)
 (if (some (lambda (feature)
	    (and (list? feature) (eq? (first feature) 'before)))
	   lexical-entry)
     (rest (find-if (lambda (feature)
		     (and (list? feature) (eq? (first feature) 'before)))
		    lexical-entry))
     '()))

(define (class track)
 (let ((class
	(voc4-detection-model
	 (find-if (lambda (box)
		   (and (voc4-detection-model box)
		      (not (string=? (voc4-detection-model box) "padding"))
		      (not (string=? (voc4-detection-model box) "."))))
		  track))))
  (if (or (string=? class "person-crouch") (string=? class "person-down"))
      "person"
      class)))

(define (get-conditional-before lexical-entry patient-track)
 (if (some (lambda (feature)
	    (and (list? feature) (eq? (first feature) 'conditional-before)))
	   lexical-entry)
     (let ((feature (find-if (lambda (feature)
			      (and (list? feature)
				 (eq? (first feature) 'conditional-before)))
			     lexical-entry)))
      (if (and patient-track (member (class patient-track) (third feature)))
	  (second feature)
	  '()))
     '()))

(define (get-after lexical-entry)
 (if (some (lambda (feature)
	    (and (list? feature) (eq? (first feature) 'after)))
	   lexical-entry)
     (rest (find-if (lambda (feature)
		     (and (list? feature) (eq? (first feature) 'after)))
		    lexical-entry))
     '()))

(define (get-restriction lexical-entry)
 (if (some (lambda (feature)
	    (and (list? feature) (eq? (first feature) 'restriction)))
	   lexical-entry)
     (rest (find-if (lambda (feature)
		     (and (list? feature) (eq? (first feature) 'restriction)))
		    lexical-entry))
     #f))

(define (get-default lexical-entry)
 (if (some (lambda (feature)
	    (and (list? feature) (eq? (first feature) 'default)))
	   lexical-entry)
     (second (find-if (lambda (feature)
		       (and (list? feature) (eq? (first feature) 'default)))
		      lexical-entry))
     #f))

(define (get-conditional-default lexical-entry patient-track np)
 (if (some (lambda (feature)
	    (and (list? feature) (eq? (first feature) 'conditional-default)))
	   lexical-entry)
     (let ((feature (find-if
		     (lambda (feature)
		      (and (list? feature)
			 (eq? (first feature) 'conditional-default)))
		     lexical-entry)))
      (if (and patient-track
	     (member (class patient-track) (rest (rest feature))))
	  np
	  (second feature)))
     np))

(define (refered-to-in-subject? track subject-track)
 (and track
    (not (eq? (generate-direction subject-track) 'none))
    (< (magnitude (track-motion track)) *motion-threshold*)
    (< (magnitude (track-motion subject-track)) *motion-threshold*)))

(define (dummy-box? box)
 (and (= (voc4-detection-x1 box) -1) (= (voc4-detection-y1 box) -1)
    (= (voc4-detection-x2 box) -1) (= (voc4-detection-y2 box) -1)))

(define *maximum-score-variance* 0.1)
(define *maximum-size-variance* 0.15)
(define *maximum-shape-variance* 0.13)

(define (estimate-size-and-shape track)
 (define (closer v l u) (if (< (- v l) (- u v))l u))
 (let* ((track (remove-if dummy-box? track))
	(aspect-ratios (map voc4-detection-aspect-ratio track))
	(areas (map voc4-detection-area track))
	(strength-variance (list-variance (map voc4-detection-strength track)))
	(class (class track)))
  (if (and (<= strength-variance *maximum-score-variance*)
	 (<= (list-variance areas) *maximum-size-variance*)
	 (<= (list-variance aspect-ratios) *maximum-shape-variance*))
      (let* ((size (/ (list-mean areas) (* 1280 720))) ; relative to image
	     (ht (/ (list-mean (map voc4-detection-height track)) 720)) ; relative to image
	     (wd (/ (list-mean (map voc4-detection-width track)) 1280)) ; relative to image
	     (ar (list-mean aspect-ratios))
	     (size-bounds (lookup-value class *class->size-adjective-bounds*))
	     (min-size (first size-bounds))
	     (max-size (second size-bounds))
	     (c-ar (lookup-value class *class->shape-adjective-bound*)))
       (list (cond ((< size min-size) "small")
		   ((> size max-size) "big")
		   (else '()))
	     (cond ((and (<= ar (* 0.7 c-ar))
		       (or (> size max-size)
			  (= (closer size min-size max-size) max-size)))
		    "tall")
		   ((and (>= ar (* 1.3 c-ar))
		       (or (> size max-size)
			  (= (closer size min-size max-size) max-size)))
		    "wide")
		   ((and (<= ar (* 0.7 c-ar))
		       (or (< size min-size)
			  (= (closer size min-size max-size) min-size)))
		    "narrow")
		   ((and (>= ar (* 1.3 c-ar))
		       (or (< size min-size)
			  (= (closer size min-size max-size) min-size)))
		    "short")
		   (else '()))))
      '())))

(define (most-frequent-class track)
 (caar (sort (transitive-equivalence-classesp
	      equal?
	      (map
	       voc4-detection-model
	       (remove-if (lambda (box)
			   (or (not (voc4-detection-model box))
			      (string=? (voc4-detection-model box) "padding")
			      (string=? (voc4-detection-model box) ".")))
			  track)))
	     >
	     length)))

(define (generate-adjectives kind subject-track object-track)
 ;; needs work
 ;;  adjectives
 ;;   color
 ;;   aspect ratio
 ;;   size
 ;;  only generate color if needed to prevent coreference of nonpersons
 ;;  order: other, size, shape, color, restrictive modifiers
 (let* ((track (case kind
		((subject) subject-track)
		((reference object) object-track)
		(else (fuck-up))))
	(subject-class (most-frequent-class subject-track))
	(object-class (if object-track (most-frequent-class object-track) #f))
	(class (case kind
		((subject) subject-class)
		((reference object) object-class)
		(else (fuck-up))))
	(proper-class
	 (if (or (equal? class "person-crouch") (equal? class "person-down"))
	     "person"
	     class)))
  (list (if (and (not (eq? kind 'subject))
	       object-track
	       (equal? subject-class object-class))
	    "other"
	    '())
	;; size and shape
	(if (assoc proper-class *class->size-adjective*)
	    (second (assoc proper-class *class->size-adjective*))
	    (estimate-size-and-shape track))
	;; colour (useless and expensive)
	(if (assoc proper-class *class->restrictive-adjective*)
	    (second (assoc proper-class *class->restrictive-adjective*))
	    '())
	(if (and (string=? proper-class "person")
	       object-class
	       (not (equal? subject-class object-class)))
	    (cond ((string=? class "person") "upright")
		  ((string=? class "person-crouch") "crouched")
		  ((string=? class "person-down") "prone")
		  (else (fuck-up)))
	    '()))))

(define (generate-vp verb subject-track object-track)
 (let ((lexical-entry (assoc (hyphens->spaces verb) *verbs*)))
  (list
   (if (member 'aux lexical-entry) "was" '())
   (if (member 'preverbal-adverb lexical-entry)
       (generate-adverb subject-track)
       '())
   (second lexical-entry)
   (if (member 'postverbal-adverb lexical-entry)
       (generate-adverb subject-track)
       '())
   (case (third lexical-entry)
    ((not-allowed)
     (list (get-before lexical-entry)
	   (get-after lexical-entry)
	   (cond
	    ((member 'endogenous-motion-pp lexical-entry)
	     (rest (assq (generate-direction subject-track)
			 *endogenous-motion-pps*)))
	    ((member 'exogenous-motion-pp lexical-entry)
	     (rest (assq (generate-direction subject-track)
			 *exogenous-motion-pps*)))
	    (else '()))))
    ((optional)
     (if (or (not (get-restriction lexical-entry))
	     (and object-track
		  (member (class object-track)
			  (get-restriction lexical-entry))))
	 (list (if (member 'endogenous-motion-pp lexical-entry)
		   (rest (assq (generate-direction subject-track)
			       *endogenous-motion-pps*))
		   '())
	       (get-before lexical-entry)
	       (get-conditional-before lexical-entry object-track)
	       (get-conditional-default
		lexical-entry
		object-track
		(generate-object-np
		 subject-track object-track (get-default lexical-entry)))
	       (get-after lexical-entry)
	       (if (member 'exogenous-motion-pp lexical-entry)
		   (rest (assq (generate-direction subject-track)
			       *exogenous-motion-pps*))
		   '()))
	 '()))
    ((required)
     (list (get-before lexical-entry)
	   (get-conditional-before lexical-entry object-track)
	   (get-conditional-default
	    lexical-entry
	    object-track
	    (generate-object-np
	     subject-track object-track (get-default lexical-entry)))
	   (get-after lexical-entry)
	   (cond
	    ((member 'endogenous-motion-pp lexical-entry)
	     (rest (assq (generate-direction subject-track)
			 *endogenous-motion-pps*)))
	    ((member 'exogenous-motion-pp lexical-entry)
	     (rest (assq (generate-direction subject-track)
			 *exogenous-motion-pps*)))
	    (else '()))))
    (else (fuck-up))))))

(define (flatten tree)
 (if (string? tree)
     tree
     (reduce (lambda (s1 s2) (string-append s1 " " s2))
	     (remove "" (map flatten tree))
	     "")))

(define (sententify string)
 (let ((characters (string->list string)))
  (string-append
   (list->string (cons (char-upcase (first characters)) (rest characters)))
   ".")))

(define (server-specific-track-pathname server video name)
 (generic-full-pathname (format #f "/net/~a/aux/qobi/video-datasets" server)
			video
			(string-append "/" name)))

;;; Tracking, likelihoods and sentences
(define (show-result-mostly port r)
 (format port "#(RESULT ~a ~a ~a ~a ~a ~a ...)~%"
	 (result-video-name r)
	 (result-track-names r)
	 (result-features-type r)
	 (result-states r)
	 (result-verb r)
	 (result-loglk r)
	 ;; (result-names-tracks r)
	 ))

(define (result->sentence video result)
 (define (name-of named)
  (if (list? (first named)) (second (first named)) (first named)))
 (unless *quiet-mode?* (show-result-mostly #t result))
 (list
  (sententify
   (flatten
    (generate-sentence
     (result-verb result)
     (second (first (result-named-tracks result)))
     (if (< (length (result-named-tracks result)) 2)
	 #f
	 (second (second (result-named-tracks result)))))))
  (result-loglk result)
  (result-verb result)
  (result-named-tracks result)))

(define (generate-sentence verb agent-track patient-track)
 ;; switch? is mapping from semantic role to syntactic position.
 (let* ((switch? (and patient-track
		      (or (string=? verb "approach") (string=? verb "flee"))
		      (< (overall-distance agent-track) *motion-threshold*)
		      (>= (overall-distance patient-track)
			  ;; needs work: change 0.5 to >1
			  (* 0.5 (overall-distance agent-track)))))
	(subject-track (if switch? patient-track agent-track))
	(object-track (if switch? agent-track patient-track)))
  (list (generate-subject-np subject-track object-track)
	(generate-vp verb subject-track object-track))))

(define (assoc-checked e l)
 (unless (assoc e l)
  (panic "Object lookup failed ~a ~a~%" e l))
 (assoc e l))

(define (generate-reference-np subject-track object-track)
 (list (generate-determiner 'reference subject-track object-track)
       (generate-adjectives 'reference subject-track object-track)
       (second (assoc-checked (class object-track) *class->noun*))))

(define (generate-subject-np subject-track object-track)
 (list
  (generate-determiner 'subject subject-track object-track)
  (generate-adjectives 'subject subject-track object-track)
  (second (assoc-checked (class subject-track) *class->noun*))
  (if (refered-to-in-subject? object-track subject-track)
      (list (rest (assq (generate-direction subject-track) *prepositions*))
	    (generate-reference-np subject-track object-track))
      '())))

(define (generate-object-np subject-track object-track default)
 (cond
  (object-track
   (list (generate-determiner 'object subject-track object-track)
 	 (generate-adjectives 'object subject-track object-track)
 	 (second (assoc-checked (class object-track) *class->noun*))))
  (default (cond ((and (string=? default "itself")
		       (string=? (class subject-track) "person"))
		  "themselves")
		 ((and (string=? default "themselves")
		     (not (string=? (class subject-track) "person")))
		  "itself")
		 (else default)))
  (else "something")))

(define (generate-determiner kind subject-track object-track)
 ;; needs work: general coreference predicate
 ;;             other doesn't enter into this
 (cond
  ((not (and object-track
	   (equal? (class subject-track) (class object-track))))
   "the")
  ((and (eq? kind 'object) (refered-to-in-subject? object-track subject-track))
   "that")
  (else "some")))

(define (result->tracks result) (result-named-tracks result))

(define (annotated-models)
 (append
  (map
   (o fields swap-commas-and-spaces)
   (safe-read-file
    (string-append
     (getenv "HOME")
     "/darpa-collaboration/documentation/C-D1-recognition-annotations.csv")
    '()))
  (map
   (o fields swap-commas-and-spaces)
   (safe-read-file
    (string-append
     (getenv "HOME")
     "/darpa-collaboration/documentation/C-E1-description-annotations.csv")
    '()))
  (map
   (o fields swap-commas-and-spaces)
   (safe-read-file
    (string-append
     (getenv "HOME")
     "/darpa-collaboration/documentation/C-E1-minus-C-D1-annotations.csv")
    '()))))

(define (annotated-models-for-video video)
 (let ((video-annotation
	(find-if
	 (lambda (annotation) (equal? (first annotation) (any-video->string video)))
	 (annotated-models))))
  (if video-annotation
      (let ((models (map first (group-nth (drop 2 video-annotation) 4))))
       (for-each
	(lambda (model)
	 (unless (file-exists? (cuda-model-pathname model))
	  (format
	   #t
	   "Ignoring annotated model ~a because it lacks trained model files~%"
	   model)))
	models)
       (map
	(lambda (ms) (if (list? ms) ms (list ms)))
	(let ((models
	       (remove-if-not (o file-exists? cuda-model-pathname) models)))
	 (if (find "person" models)
	     (cons '("person" "person-crouch" "person-down")
		   (remove "person" models))
	     models))))
      #f)))

(define (scale-detector-output list-of-list-voc4-boxes video-width->1280)
 (map (lambda (list-voc4-boxes)
       (map (lambda (voc4-box)
             (voc4-scale-abs voc4-box video-width->1280))
            list-voc4-boxes)) list-of-list-voc4-boxes))

;;; Viterbi sentence tracker --------------------------------------------------

(define-structure cfg rules)
(define-structure production-rule lhs rhs)
(define (create-cfg rules)
 (unless (and (not (some list? (map production-rule-lhs rules)))
	    (not (every list? (map production-rule-lhs rules))))
  (panic "Production rule: NT (terminals* U NT*)"))
 (make-cfg rules))
(define p-lhs production-rule-lhs)
(define p-rhs production-rule-rhs)

(define (*toy-corpus:semantics* lexical-entry)
 (let ((all-roles '(agent patient referent goal source)))
  (case lexical-entry
   ((to-the-left-of) `((agent patient) (referent)))
   ((to-the-right-of) `((agent patient) (referent)))
   ((picked-up) `((agent) (patient)))
   ((put-down) `((agent) (patient)))
   ((carried) `((agent) (patient)))
   ((approached) `((agent) (goal)))
   ((towards) `((agent patient) (goal)))
   ((away-from) `((agent patient) (source)))
   (else `(,all-roles)))))

(define *toy-corpus:cfg*
 (create-cfg
  (list (make-production-rule 'S '(NP VP))
	(make-production-rule 'NP '(D (A) N (PP)))
	(make-production-rule 'D '(an))
	(make-production-rule 'D '(the))
	(make-production-rule 'A '(green))
	(make-production-rule 'A '(red))
	(make-production-rule 'A '(yellow))
	(make-production-rule 'N '(person))
	(make-production-rule 'N '(giraffe))
	(make-production-rule 'N '(gun))
	(make-production-rule 'N '(sign))
	(make-production-rule 'N '(object))
	(make-production-rule 'PP '(P NP))
	(make-production-rule 'P '(to the left of))
	(make-production-rule 'P '(to the right of))
	(make-production-rule 'VP '(V NP (ADV) (PPM)))
	(make-production-rule 'V '(picked up))
	(make-production-rule 'V '(put down))
	(make-production-rule 'V '(carried))
	(make-production-rule 'V '(approached))
	(make-production-rule 'ADV '(quickly))
	(make-production-rule 'ADV '(slowly))
	(make-production-rule 'PPM '(PM NP))
	(make-production-rule 'PM '(towards))
	(make-production-rule 'PM '(away from)))))

(define (*new3-corpus:semantics* lexical-entry)
 (let ((all-roles '(agent patient referent goal source)))
  (case lexical-entry
   ((to-the-left-of) `((agent patient goal) (referent)))
   ((to-the-right-of) `((agent patient goal) (referent)))
   ((picked-up) `((agent) (patient)))
   ((put-down) `((agent) (patient)))
   ((carried) `((agent) (patient)))
   ((approached) `((agent) (goal)))
   ((towards) `((agent patient) (goal)))
   ((away-from) `((agent patient) (source)))
   (else `(,all-roles)))))

(define (*new4-corpus:semantics* lexical-entry)
 (let ((all-roles '(agent patient referent goal source)))
  (case lexical-entry
   ((to-the-left-of) `((agent patient goal) (referent)))
   ((to-the-right-of) `((agent patient goal) (referent)))
   ((picked-up) `((agent) (patient)))
   ((put-down) `((agent) (patient)))
   ((carried) `((agent) (patient)))
   ((approached) `((agent) (goal)))
   ((towards) `((agent patient) (goal)))
   ((away-from) `((agent patient) (source)))
   ((gave) `((agent) (patient) (goal)))
   ((replaced) `((agent) (patient) (goal)))
;;   ((followed) `((agent) (patient)))
   ((left) `((agent) (goal)))
   ((collided-with) `((agent) (patient)))
   ((with) `((patient) (goal)))
   (else `(,all-roles)))))

(define *new3-corpus:cfg*
 (create-cfg
  (list (make-production-rule 'S '(NP VP))
	(make-production-rule 'NP '(D (A) N (PP)))
	(make-production-rule 'D '(an))
	(make-production-rule 'D '(the))
	(make-production-rule 'A '(blue))
	(make-production-rule 'A '(red))
	(make-production-rule 'N '(person))
	(make-production-rule 'N '(backpack))
	(make-production-rule 'N '(trash-can))
	(make-production-rule 'N '(chair))
	(make-production-rule 'N '(object))
	(make-production-rule 'PP '(P NP))
	(make-production-rule 'P '(to the left of))
	(make-production-rule 'P '(to the right of))
	(make-production-rule 'VP '(V NP (ADV) (PPM)))
	(make-production-rule 'V '(picked up))
	(make-production-rule 'V '(put down))
	(make-production-rule 'V '(carried))
	(make-production-rule 'V '(approached))
	(make-production-rule 'ADV '(quickly))
	(make-production-rule 'ADV '(slowly))
	(make-production-rule 'PPM '(PM NP))
	(make-production-rule 'PM '(towards))
	(make-production-rule 'PM '(away from)))))

(define *new4-corpus:cfg*
 (create-cfg
  (list (make-production-rule 'S '(NP VP))
	(make-production-rule 'NP '(D (A) N (PP)))
	(make-production-rule 'D '(the))
	(make-production-rule 'A '(blue))
	(make-production-rule 'A '(red))
	(make-production-rule 'N '(person))
	(make-production-rule 'N '(backpack))
	(make-production-rule 'N '(trash-can))
	(make-production-rule 'N '(chair))
	(make-production-rule 'N '(traffic-cone))
	(make-production-rule 'N '(stool))
	(make-production-rule 'N '(object))
	(make-production-rule 'PP '(PL NP))
	(make-production-rule 'PL '(to the left of))
	(make-production-rule 'PL '(to the right of))
	(make-production-rule 'P '(with))
	(make-production-rule 'P '(to))
	(make-production-rule 'VP '(TV NP (ADV) (PPM)))
	(make-production-rule 'VP '((ADV) DTV NP P NP))
	(make-production-rule 'TV '(picked up))
	(make-production-rule 'TV '(put down))
	(make-production-rule 'TV '(carried))
	(make-production-rule 'TV '(approached))
	(make-production-rule 'TV '(collided with))
	(make-production-rule 'TV '(left))
	(make-production-rule 'DTV '(gave))
	(make-production-rule 'DTV '(replaced))
	(make-production-rule 'ADV '(quickly))
	(make-production-rule 'ADV '(slowly))
	(make-production-rule 'PPM '(PM NP))
	(make-production-rule 'PM '(towards))
	(make-production-rule 'PM '(away from)))))

(define (*needle-in-a-haystack:semantics* lexical-entry)
 (let ((all-roles '(agent patient referent goal source)))
  (case lexical-entry
   ((to-the-left-of) `((agent patient) (referent)))
   ((to-the-right-of) `((agent patient) (referent)))
   ((drove) `((agent)))
   ((rode) `((agent) (patient)))
   ((carried) `((agent) (patient)))
   ((approached) `((agent) (goal)))
   ((from-the-left from-the-right) `((agent) (goal)))
   (else `(,all-roles)))))

(define *needle-in-a-haystack:cfg*
 (create-cfg
  (list (make-production-rule 'S '(NP VP))
	(make-production-rule 'NP '(D (A) N (PP)))
	(make-production-rule 'D '(an))
	(make-production-rule 'D '(the))
	(make-production-rule 'A '(green))
	(make-production-rule 'A '(red))
	(make-production-rule 'A '(yellow))
	(make-production-rule 'N '(person))
	(make-production-rule 'N '(bicycle))
	(make-production-rule 'N '(car))
	(make-production-rule 'N '(object))
	(make-production-rule 'PP '(P NP))
	(make-production-rule 'P '(to the left of))
	(make-production-rule 'P '(to the right of))
	(make-production-rule 'VP '(V NP (ADV) (PPM) (AJ)))
	(make-production-rule 'V '(drove))
	(make-production-rule 'V '(rode))
	(make-production-rule 'V '(carried))
	(make-production-rule 'V '(approached))
	(make-production-rule 'ADV '(quickly))
	(make-production-rule 'ADV '(slowly))
	(make-production-rule 'AJ '(from-the-left))
	(make-production-rule 'AJ '(from-the-right))
	(make-production-rule 'PPM '(PM NP))
	(make-production-rule 'PM '(towards))
	(make-production-rule 'PM '(away from)))))

(define (cfg:a-valid-rhs rhs)
 (remove #f (nondeterministic-map (lambda (r) (if (list? r) (either #f (car r)) r)) rhs)))

(define (cfg:non-terminal? symbol cfg)
 (member symbol (map p-lhs (cfg-rules cfg))))

(define (cfg:terminal? symbol cfg)
 (not (cfg:non-terminal? symbol cfg)))

(define (cfg:terminals cfg)
 (set-difference (flatten* (map p-rhs (cfg-rules cfg))) (map p-lhs (cfg-rules cfg))))

(define (cfg:non-terminals cfg)
 (remove-duplicates (map p-lhs (cfg-rules cfg))))

(define (cfg:terminal-categories cfg)
 (remove-duplicates
  (removeq
   #f
   (map (lambda (r) (and (not (some (lambda (t) (cfg:non-terminal? t cfg)) (p-rhs r))) (p-lhs r)))
	(cfg-rules cfg)))))

(define (cfg:optional-categories-with-rules cfg)
 (map
  (lambda (c) (list (caar c) (map second c)))
  (transitive-equivalence-classesp
   (lambda (a b) (equal? (car a) (car b)))
   (map-reduce
    append
    '()
    (lambda (r) (map (lambda (a) (list (car a) r)) (remove-if-not list? (production-rule-rhs r))))
    (cfg-rules cfg)))))

(define (cfg:possible-rules lhs rules)
 (remove-if-not (lambda (r) (equal? (p-lhs r) lhs)) rules))

(define (cfg:lexicalized-terminals cfg)
 (map
  (lambda (rhs) (string-join " " (map symbol->string rhs)))
  (remove-if
   (lambda (r) (or (some list? r) (some (lambda (t) (cfg:non-terminal? t cfg)) r)))
   (map p-rhs (cfg-rules cfg)))))

;; this used to be based on the terminals, but it needs to be based on the rules as well
;; since terminals are shared across rules.
(define (lexicalize es cfg . symbol)
 (map (lambda (t) (string->symbol
	      (string-join (if (null? symbol) "-" (symbol->string (car symbol)))
			   (map symbol->string (rest t)))))
      (tree->leaves (sentence:parse-sentence-any (unwords (map symbol->string es)) cfg) p-leaf?)))

(define (lexicalize-phrase phrase cfg)
 (string-join
  "_"
  (map (o string-downcase symbol->string)
       (lexicalize (map string->symbol (fields phrase)) cfg '+))))

;; optionally takes a start non-terminal to begin parsing from
(define (sentence:parse-sentence sentence cfg . start)
 (define rules (cfg-rules cfg))
 (define terminals (cfg:terminals cfg))
 (define tokens (map string->symbol (fields (string-upcase sentence))))
 (define (num-terminals p) (length (intersection (flatten* p) terminals)))
 (define (longest-parse parses) (if (null? parses) '() (maximump parses num-terminals)))
 (find-if
  (lambda (p) (equal? (intersection (flatten* p) terminals) tokens))
  (map
   (lambda (start-rule)
    (let loop ((rule start-rule) (stack (take 1 tokens)) (tokens (cdr tokens)))
     (cond
      ((equal? (p-rhs rule) stack) (cons (p-lhs rule) stack))
      ((initial-sublist? (p-rhs rule) stack)
       (if (null? tokens)
	   '()
	   (loop rule (append stack (take 1 tokens)) (cdr tokens))))
      (else (longest-parse
	     (all-values
	      (cons (p-lhs rule)
		    (nondeterministic-map
		     (lambda (r)
		      (let* ((poss (cfg:possible-rules r rules))
			     (parse (if (null? poss) (fail) (loop (a-member-of poss) stack tokens))))
		       (if (null? parse)
			   (fail)
			   (begin
			    (local-set! tokens (drop (- (num-terminals parse) (length stack)) tokens))
			    (unless (null? tokens)
			     (local-set! stack (take 1 tokens))
			     (local-set! tokens (cdr tokens)))
			    parse))))
		     (cfg:a-valid-rhs (p-rhs rule))))))))))
   (remove-if-not (lambda (r) (eq? (p-lhs r) (if (null? start) 'S (car start)))) rules))))

(define (sentence:parse-sentence-any sentence cfg)
 (call-with-current-continuation
  (lambda (return)
   (for-each
    (lambda (nt) (let ((parse (sentence:parse-sentence sentence cfg nt))) (when parse (return parse))))
    (cfg:non-terminals cfg)))))

(define (sentence-data-filename video)
 (generic-full-pathname *video-pathname* video "/sentence-data.sc"))

(define (sentence-specific-data-filename video sentence)
 (generic-full-pathname *video-pathname* video (format #f "/~a.sc" sentence)))

;; This searches each level going up the tree till it's roles are filled
(define (theta-role-assignments sentence cfg semantics)
 (define parse (sentence:parse-sentence-any sentence cfg))
 (define terminal-categories (cfg:terminal-categories cfg))
 (define (head-noun tree)
  (let ((n (find-if (lambda (e) (eq? (car e) 'N)) (tree->leaves tree ip-leaf?))))
   (unless n (panic "head-noun: noun not pund in parse:~%~a~%" tree))
   `#(,(car (lexicalize (take-until vector? (rest n)) cfg)) ,(vector->list (last n)))))
 (define (get-noun-terms ztree category)
  (map head-noun (remove-if-not (lambda (t) (eq? (car t) category)) (rest (zipper-tree ztree)))))
 (define (assign-roles ztree)
  (if (member (car (zipper-tree ztree)) terminal-categories)
      (let ((terminal (car (lexicalize (take-until vector? (cdr (zipper-tree ztree))) cfg))))
       (append
	(list (car (zipper-tree ztree)) terminal)
	(let loop ((ztree ztree) (terms '()) (l (length (semantics terminal))))
	 (if (<= l 0)
	     (take (length (semantics terminal)) terms)
	     (if (zipper:can-ascend? ztree)
		 (let* ((new-ztree (zipper:ascend ztree))
			(current-ns (get-noun-terms new-ztree 'N))
			(current-nps (get-noun-terms new-ztree 'NP)))
		  (loop new-ztree
			(append current-ns current-nps terms)
			(- l (length current-ns) (length current-nps))))
		 (loop ztree (cons `#(thing (,l)) terms) (- l 1)))))))
      (cons (car (zipper-tree ztree))
	    (all-values
	     (assign-roles
	      (zipper:descend ztree (an-integer-between 1 (- (length (zipper-tree ztree)) 1))))))))
 (unless parse (panic "Failed parse~%  sentence: ~a~%" sentence))
 (tree->leaves (assign-roles (zipper:initialize (index-leaves parse p-leaf? '()))) ip-leaf?))

;; needs to handle roles better - quickly/slowly - based on verb
(define (sentence:sentence->participants-roles-and-state-machines sentence cfg semantics state-machines)
 (define (reorder-roles roles)
  ;; this is a hack for hmms (they need order)
  (let* ((a (assoc 'agent roles)) (p (assoc 'patient roles)) (g (assoc 'goal roles)))
   (cond (p (append (list a p) (set-difference roles (list a p))))
	 (g (append (list a g) (set-difference roles (list a g))))
	 (else roles))))
 (unless (or (every fsm? state-machines) (every trained-hmm? state-machines))
  (panic "State machines have to be either only fsms or only hmms"))
 (let* ((theta-roles (theta-role-assignments sentence cfg semantics))
	;; this should probably be a parameter of corpora
	(all-roles '(agent patient referent goal source))
	(participant-role-pairs
	 (reorder-roles
	  (map
	   (lambda (g)
	    (let ((r (map-reduce intersection all-roles second g)))
	     (when (null? r) (panic "Inconsistent role assignments: ~a" g))
	     (when (> (length r) 1) (format #t "Ambiguous role assignments: ~a~%" g))
	     (list (first r) (caar g))))
	   (group-by
	    first
	    (join
	     (map
	      (lambda (e)
	       (let* ((role-entities (drop-until vector? e)) (roles (semantics (second e))))
		(unless (= (length roles) (length role-entities))
		 (panic "Semantics and assignments don't match!:~% semantics: ~a~% roles: ~a~%"
			roles role-entities))
		(zip role-entities roles)))
	      theta-roles))))))
	(machine-and-roles
	 (removeq
	  #f
	  (map
	   (lambda (e)
	    (let* ((role-entities (drop-until vector? e))
		   (sm (find-if (lambda (sm) (equal? (second e) (state-machine-name sm))) state-machines))
		   (participants (map second participant-role-pairs)))
	     (and
	      sm
	      (let ((ps (map (lambda (r) (position r participants)) role-entities)))
	       ;; (format #t "~a: ~a~%" (state-machine-name sm) ps)
	       (cond
		((fsm? sm)
		 (list
		  (let ((fsm (map-vector identity sm)))
		   (set-fsm-preconditions!
		    fsm
		    (map-vector
		     (lambda (p) (lambda (scale transformations . entities)
				  (apply p scale transformations (map (lambda (i) (list-ref entities i)) ps))))
		     (fsm-preconditions sm)))
		   fsm)
		  ps))
		((trained-hmm? sm) (list sm ps))
		(else (fuck-up)))))))
	   theta-roles))))
  `(,(map first machine-and-roles)
    ,(zip (map first participant-role-pairs)
	  (map (o string-downcase symbol->string x second) participant-role-pairs))
    ,(map second machine-and-roles)
    ,theta-roles)))

(define (state-machine-name sm)
 (cond ((fsm? sm) (fsm-name sm))
       ((trained-hmm? sm) (trained-hmm-name sm))
       (else (fuck-up))))

(define-structure fsm name preconditions transitions)

(define (fsm-assert-preconditions fsms states scale transformation
				  boxes-movie-so-far new-boxes lookahead)
 (let ((boxes (boxes-movie->box-movies
               (reverse
                (pad-with-last-if-necessary
                 (cons new-boxes boxes-movie-so-far) lookahead)))))
  (every (lambda (fsm state)
          (format #t ">> ~a ~a ~a~%" (fsm-name fsm)
                  state
                  (apply (vector-ref (fsm-preconditions fsm) state)
			 scale transformation boxes))
          (apply (vector-ref (fsm-preconditions fsm) state)
		 scale transformation boxes))
         fsms states)))

(define (fsm-assert-transitions fsms states-movie states)
 (every (lambda (fsm old-state new-state)
         (matrix-ref (fsm-transitions fsm)  old-state new-state))
        fsms (first states-movie) states))

(define (test-fsms tracks fsms transformations scale lookahead)
 (if (fsm-assert-preconditions fsms (map (const 0) fsms) scale
                               '(()) '() (map first tracks) lookahead)
     (possibly?
      (let loop ((states-movie (list (map (const 0) fsms)))
		 (boxes-movie (list (map first tracks)))
		 (tracks (map cdr tracks))
		 (transformations (cdr transformations)))
       (cond ((null? tracks) states-movie)
	     ((= (length (car tracks)) 1)
              (unless (every (lambda (fsm s)
                              (= s (- (vector-length (fsm-preconditions fsm)) 1)))
                             fsms (car states-movie))
               (fail))
	      (reverse states-movie))
	     (else
	      (let ((new-states (a-set-of-states fsms 'fsms)))
	       (unless
		 (and (fsm-assert-transitions fsms states-movie new-states)
		    (fsm-assert-preconditions
		     fsms new-states scale
		     transformations '() ;; boxes-movie
		     (map first tracks) lookahead))
		(fail))
	       (loop (cons new-states states-movie)
		     (cons (map first tracks) boxes-movie)
		     (map cdr tracks)
		     (cdr transformations)))))))
     #f))

(define (test-fsms-states tracks fsms transformations scale lookahead states)
 (if (fsm-assert-preconditions fsms (map (const 0) fsms) scale
                               '(()) '() (map first tracks) lookahead)
     (let loop ((states-movie (list (first states)))
                (states (cdr states))
                (boxes-movie (list (map first tracks)))
                (tracks (map cdr tracks))
                (transformations (cdr transformations))
                (i 1))
      (cond ((null? tracks) (reverse states-movie))
            ((= (length (car tracks)) 1) (reverse states-movie))
            (else
             (let ((new-states (first states)))
              (unless
                (and (fsm-assert-transitions fsms states-movie new-states)
		   (fsm-assert-preconditions fsms new-states scale transformations
					     '() (map first tracks) lookahead))
               (fuck-up))
              (loop (cons new-states states-movie)
                    (cdr states)
                    (cons (map first tracks) boxes-movie)
                    (map cdr tracks)
                    (cdr transformations)
                    (+ i 1))))))
     #f))

(define (sentence:fsm name predicates)
 (define (no-jump scale transformations agent patient . _)
  (if (> (length agent) 1)
      (<= (distance  (voc4-detection-center (voc4-scale-abs (first agent) scale))
		    (voc4-detection-center (voc4-scale-abs (second agent) scale)))
	 20)				; pixels w.r.t 640x480
      #t))
 (define (add-fsm-slack fsm slack allow-end-state-transitions?)
  (cond ((= (vector-length (fsm-preconditions fsm)) 3)
         (make-fsm (fsm-name fsm)
                   (list->vector
                    `(,(vector-ref (fsm-preconditions fsm) 0)
                      ,(vector-ref (fsm-preconditions fsm) 1)
                      ,@(map-n (lambda _ (lambda _ no-jump)) slack)
                      ,(vector-ref (fsm-preconditions fsm) 2)))
                   (list->vector
                    (map list->vector
                         `((#t #t ,@(map-n (const #f) slack)  #f)
                           ,@(map-n
			      (lambda (i)
			       `(#f #t ,@(map-n (lambda (j) (= i j)) slack)
				    ,(if allow-end-state-transitions? #t (= i 0))))
			      (+ slack 1))
                           (#f #f ,@(map-n (const #f) slack) #t))))))
        (else fsm)))        ; slack for non-3-state-fsms unimplemented
 (let ((slack 1) (allow-end-state-transitions? #t))
  (add-fsm-slack
   (make-fsm name predicates
             (cond ((= (vector-length predicates) 3)
		    `#(#(#t #t #f) #(#f #t #t) #(#f #f #t)))
                   ((= (vector-length predicates) 1) `#(#(#t)))
                   (else (fuck-up))))
   slack allow-end-state-transitions?)))

(define *toy-corpus:fsms*
 (let* ((far 300)
        (close 300)                     ;
        (stationary 6)                  ; < flow
        (moving-towards -10)            ; < delta relative distance
        (moving-away-from 10)           ; > delta relative distance
        (closing-delta 5)
        (angle-delta (degrees->radians 45))
        (quickly-delta 30)
        (pp-delta 50)
        (color-delta (degrees->radians 30))
        (slowly-delta 30)
        (far-f (lambda (agent patient)
                (> (abs (- (x (voc4-detection-center (first agent)))
                           (x (voc4-detection-center (first patient)))))
                   far)))
        (close-f (lambda (agent patient)
                  (< (abs (- (x (voc4-detection-center (first agent)))
                             (x (voc4-detection-center (first patient)))))
                     close)))
        (stationary-f
         (lambda (agent transformations scale)
          (if (first transformations)
              (begin
               (<= (magnitude
		   (average-flow-in-box (voc4-scale-abs (first agent) scale)
					(first transformations)))
		  stationary))
              #t)))
        (closing-f (lambda (agent patient transformations scale)
                    (> (abs (- (x (voc4-detection-center (first agent)))
                               (x (voc4-detection-center (first patient)))))
                       (+ (abs (- (x (voc4-detection-center
                                      (forward-project-box-scaled
                                       (first agent) (first transformations) 1 scale)))
                                  (x (voc4-detection-center (first patient)))))
                          closing-delta))))
        (departing-f (lambda (agent patient transformations scale)
                      (< (abs (- (x (voc4-detection-center (first agent)))
                                 (x (voc4-detection-center (first patient)))))
                         (+ (abs (- (x (voc4-detection-center
                                        (forward-project-box-scaled
                                         (first agent) (first transformations) 1 scale)))
                                    (x (voc4-detection-center (first patient)))))
                            closing-delta))))
        (moving-direction-f
         (lambda (agent transformations scale direction)
          (and (< (angle-separation
		   (orientation (average-flow-in-box (voc4-scale-abs (first agent) scale)
						     (first transformations)))
		   direction)
		  angle-delta)
	       (> (magnitude
		   (average-flow-in-box (voc4-scale-abs (first agent) scale)
					(first transformations)))
		  stationary))))
        (is-object-class-f
         (lambda (class) (lambda (scale transformations agent . _)
		     (equal? (voc4-detection-model (first agent)) class))))
        (has-color-f
         (lambda (color) (lambda (scale transformations agent . _)
		     (< (angle-separation
			 (x (hsv-degrees->hsv-radians
			     (unpack-color (voc4-detection-color (first agent)))))
			 color)
			color-delta))))
        (stationary-but-far
         (lambda (scale transformations agent patient . _)
          (and (far-f agent patient)
	     (stationary-f agent transformations scale)
	     (stationary-f patient transformations scale))))
        (stationary-but-close
         (lambda (scale transformations agent patient . _)
          (and (close-f agent patient)
	     (stationary-f agent transformations scale)
	     (stationary-f patient transformations scale))))
        (approaching
         (lambda (scale transformations agent patient . _)
          (and (closing-f agent patient transformations scale)
	     (stationary-f patient transformations scale))))
        (departing
         (lambda (scale transformations agent patient . _)
          (and (departing-f agent patient transformations scale)
	     (stationary-f patient transformations scale))))
        (picking-up
         (lambda (scale transformations agent patient . _)
          (and (stationary-f agent transformations scale)
	     (moving-direction-f patient transformations scale (- half-pi)))))
        (putting-down
         (lambda (scale transformations agent patient . _)
          (and (stationary-f agent transformations scale)
	     (moving-direction-f patient transformations scale half-pi))))
        (carrying
         (lambda (scale transformations agent patient . _)
          (or (and (moving-direction-f agent transformations scale 0)
		(moving-direction-f patient transformations scale 0))
	     (and (moving-direction-f agent transformations scale pi)
		(moving-direction-f patient transformations scale pi)))))
        (quickly
         (lambda (scale transformations agent patient . _)
          (> (magnitude (average-flow-in-box (voc4-scale-abs (first agent) scale)
					     (first transformations)))
             quickly-delta)))
        (slowly
         (lambda (scale transformations agent patient . _)
          (< stationary
             (magnitude (average-flow-in-box (voc4-scale-abs (first agent) scale)
					     (first transformations)))
             slowly-delta)))
        (left-of
         (lambda (scale transformations agent patient . _)
          (< (x (voc4-detection-center (first agent)))
             (- (x (voc4-detection-center (first patient))) pp-delta))))
        (right-of
         (lambda (scale transformations agent patient . _)
          (> (x (voc4-detection-center (first agent)))
             (+ (x (voc4-detection-center (first patient))) pp-delta)))))
  (list
   (sentence:fsm
    'approached `#(,stationary-but-far ,approaching ,stationary-but-close))
   (sentence:fsm
    'put-down `#(,stationary-but-close ,putting-down ,stationary-but-close))
   (sentence:fsm
    'picked-up `#(,stationary-but-close ,picking-up ,stationary-but-close))
   (sentence:fsm
    'carried `#(,stationary-but-close ,carrying ,stationary-but-close))
   ;; nouns
   (sentence:fsm 'person `#(,(is-object-class-f "person")))
   (sentence:fsm 'giraffe `#(,(is-object-class-f "giraffe")))
   (sentence:fsm 'gun `#(,(is-object-class-f "gun")))
   (sentence:fsm 'sign `#(,(is-object-class-f "sign")))
   (sentence:fsm 'object
                 `#(,(lambda (scale transformations agent . _)
                      (member (voc4-detection-model (first agent))
			 '("giraffe" "gun" "sign")))))
   ;; adverbs
   (sentence:fsm 'quickly `#(,(const #t) ,quickly ,(const #t)))
   (sentence:fsm 'slowly `#(,(const #t) ,slowly ,(const #t)))
   ;; prepositions
   (sentence:fsm 'to-the-left-of `#(,left-of))
   (sentence:fsm 'to-the-right-of `#(,right-of))
   ;; motion prepositions
   (sentence:fsm 'towards `#(,stationary-but-far ,approaching ,stationary-but-close))
   (sentence:fsm 'away-from `#(,stationary-but-close ,departing ,stationary-but-far))
   ;; adjectives
   (sentence:fsm 'red `#(,(has-color-f (degrees->radians 0))))
   (sentence:fsm 'green `#(,(has-color-f (degrees->radians 120))))
   (sentence:fsm 'yellow `#(,(has-color-f (degrees->radians 60)))))))

(define *new3-corpus:fsm*
 (let* ((far 300)
        (close 300)                     ;
        (stationary 6)                  ; < flow
	(min-stationary-jump 10)	; pixels
	(max-stationary-jump 30)	; pixels
	(max-orthogonal-jump 30)	; pixels
        (moving-towards -10)            ; < delta relative distance
        (moving-away-from 10)           ; > delta relative distance
        (closing-delta 10)
        (angle-delta (degrees->radians 30))
        (quickly-delta 80)		; pixels
        (pp-delta 50)
        (color-delta (degrees->radians 30))
        (slowly-delta 30)		; pixels
	(no-jump
	 (lambda (agent comp transformations scale)
	  (if (> (length agent) 1)
	      (<= (abs (- (comp (voc4-detection-center (voc4-scale-abs (first agent) scale)))
			 (comp (voc4-detection-center (voc4-scale-abs (second agent) scale)))))
		 max-orthogonal-jump)
	      #t)))
	(alike
	 (lambda (agent patient)
	  (and (equal? (voc4-detection-model (first agent))
		     (voc4-detection-model (first patient)))
	     ;; (>= (voc4-detection-intersection-divided-by-union (first agent) (first patient))
	     ;; 	0.7)
	     )))			; intersection/union ratio
        (far-f (lambda (agent patient)
                (> (abs (- (x (voc4-detection-center (first agent)))
                           (x (voc4-detection-center (first patient)))))
                   far)))
        (close-f (lambda (agent patient)
                  (< (abs (- (x (voc4-detection-center (first agent)))
                             (x (voc4-detection-center (first patient)))))
                     close)))
        (stationary-f
         (lambda (agent transformations scale)
          (if (first transformations)
              (<= (magnitude (average-flow-in-box (voc4-scale-abs (first agent) scale)
						 (first transformations)))
		 stationary)
              #t)))
        (closing-f (lambda (agent patient transformations scale)
		    (and (> (abs (- (x (voc4-detection-center (first agent)))
				  (x (voc4-detection-center (first patient)))))
			  (+ (abs (- (x (voc4-detection-center
					 (forward-project-box-scaled
					  (first agent) (first transformations) 1 scale)))
				     (x (voc4-detection-center (first patient)))))
			     closing-delta))
		       (no-jump agent y transformations scale)
		       (no-jump patient y transformations scale)
		       )))
        (departing-f (lambda (agent patient transformations scale)
		      (and (< (abs (- (x (voc4-detection-center (first agent)))
				    (x (voc4-detection-center (first patient)))))
			    (+ (abs (- (x (voc4-detection-center
					   (forward-project-box-scaled
					    (first agent) (first transformations) 1 scale)))
				       (x (voc4-detection-center (first patient)))))
			       closing-delta))
			 (no-jump agent y transformations scale)
			 (no-jump patient y transformations scale)
			 )))
        (moving-direction-f
         (lambda (agent transformations scale direction)
          (and (< (angle-separation
		 (orientation (average-flow-in-box (voc4-scale-abs (first agent) scale)
						   (first transformations)))
		 direction)
		angle-delta)
	     (> (magnitude
		 (average-flow-in-box (voc4-scale-abs (first agent) scale)
				      (first transformations)))
		stationary)
	     ;; (> (distance (voc4-detection-center (voc4-scale-abs (first agent) scale))
	     ;; 		  (voc4-detection-center (voc4-scale-abs (second agent) scale)))
	     ;; 	min-stationary-jump)
	     (no-jump agent
		      ;; siddharth: potential pitfall - assumes only 0 180 90 -90
		      (if (or (equal? direction 0) (equal? direction pi)) y x)
		      transformations
		      scale))))
        (is-object-class-f
         (lambda (class) (lambda (scale transformations agent . _)
		     (equal? (voc4-detection-model (first agent)) class))))
        (has-color-f
         (lambda (color) (lambda (scale transformations agent . _)
		     (< (angle-separation
			 (x (hsv-degrees->hsv-radians
			     (unpack-color (voc4-detection-color (first agent)))))
			 color)
			color-delta))))
        (stationary-but-far
         (lambda (scale transformations agent patient . _)
          (and (not (alike agent patient))
	     (far-f agent patient)
	     (stationary-f agent transformations scale)
	     (stationary-f patient transformations scale))))
        (stationary-but-close
         (lambda (scale transformations agent patient . _)
          (and (not (alike agent patient))
	     (close-f agent patient)
	     (stationary-f agent transformations scale)
	     (stationary-f patient transformations scale))))
        (approaching
         (lambda (scale transformations agent patient . _)
          (and (not (alike agent patient))
	     (closing-f agent patient transformations scale)
	     (stationary-f patient transformations scale))))
        (departing
         (lambda (scale transformations agent patient . _)
          (and (not (alike agent patient))
	     (departing-f agent patient transformations scale)
	     (stationary-f patient transformations scale))))
        (picking-up
         (lambda (scale transformations agent patient . _)
          (and (not (alike agent patient))
	     (stationary-f agent transformations scale)
	     (moving-direction-f patient transformations scale (- half-pi)))))
        (putting-down
         (lambda (scale transformations agent patient . _)
          (and (not (alike agent patient))
	     (stationary-f agent transformations scale)
	     (moving-direction-f patient transformations scale half-pi))))
        (carrying
         (lambda (scale transformations agent patient . _)
	  (and (not (alike agent patient))
	     (or (and (moving-direction-f agent transformations scale 0)
		   (moving-direction-f patient transformations scale 0))
		(and (moving-direction-f agent transformations scale pi)
		   (moving-direction-f patient transformations scale pi))))))
	(quickly
         (lambda (scale transformations agent . _)
	  (if (= (length agent) 1)
	      #t
	      (> (distance (voc4-detection-center (voc4-scale-abs (first agent) scale))
			   (voc4-detection-center (voc4-scale-abs (second agent) scale)))
		 quickly-delta
		 ))))
        (slowly
         (lambda (scale transformations agent . _)
	  (if (= (length agent) 1)
	      #t
	      (< stationary
		 (distance (voc4-detection-center (voc4-scale-abs (first agent) scale))
			   (voc4-detection-center (voc4-scale-abs (second agent) scale)))
		 slowly-delta
		 ))))
        (left-of
         (lambda (scale transformations agent patient . _)
          (< (x (voc4-detection-center (first agent)))
             (- (x (voc4-detection-center (first patient))) pp-delta))))
        (right-of
         (lambda (scale transformations agent patient . _)
          (> (x (voc4-detection-center (first agent)))
             (+ (x (voc4-detection-center (first patient))) pp-delta)))))
  (list
   (sentence:fsm
    'approached `#(,stationary-but-far ,approaching ,stationary-but-close))
   (sentence:fsm
    'put-down `#(,stationary-but-close ,putting-down ,stationary-but-close))
   (sentence:fsm
    'picked-up `#(,stationary-but-close ,picking-up ,stationary-but-close))
   (sentence:fsm
    'carried `#(,stationary-but-close ,carrying ,stationary-but-close))
   ;; nouns
   (sentence:fsm 'person `#(,(is-object-class-f "person")))
   (sentence:fsm 'backpack `#(,(is-object-class-f "backpack")))
   (sentence:fsm 'trash-can `#(,(is-object-class-f "trash-can")))
   (sentence:fsm 'chair `#(,(is-object-class-f "chair")))
   (sentence:fsm 'object
                 `#(,(lambda (scale transformations agent . _)
                      (member (voc4-detection-model (first agent))
			 '("backpack" "trash-can" "chair")))))
   ;; adverbs
   (sentence:fsm 'quickly `#(,(const #t) ,quickly ,(const #t)))
   (sentence:fsm 'slowly `#(,(const #t) ,slowly ,(const #t)))
   ;; prepositions
   (sentence:fsm 'to-the-left-of `#(,left-of))
   (sentence:fsm 'to-the-right-of `#(,right-of))
   ;; motion prepositions
   (sentence:fsm 'towards `#(,stationary-but-far ,approaching ,stationary-but-close))
   (sentence:fsm 'away-from `#(,stationary-but-close ,departing ,stationary-but-far))
   ;; adjectives
   (sentence:fsm 'red `#(,(has-color-f (degrees->radians 0))))
   (sentence:fsm 'blue `#(,(has-color-f (degrees->radians 225))))
   ;; new3 chair colour not possible to extract
   ;; (sentence:fsm 'grey `#(,(has-color-f 00)))
   )))

(define *needle-in-a-haystack:fsms*
 (let* (
        (far 180)
        (close 120)                     ;
        (stationary 2)                  ; < flow
        (closing-delta 3)
        (angle-delta (degrees->radians 45))
        (quickly-delta 30)
        (pp-delta 50)
        (color-delta (degrees->radians 30))
        (slowly-delta 30)

        (far-f (lambda (agent patient)
                (> (- (abs (- (x (voc4-detection-center (first agent)))
                              (x (voc4-detection-center (first patient)))))
                      (/ (voc4-detection-width (first agent)) 2)
                      (/ (voc4-detection-width (first patient)) 2))
                   far)))
        (really-close-f (lambda (agent patient)
                         (< (- (abs (- (x (voc4-detection-center (first agent)))
                                       (x (voc4-detection-center (first patient)))))
                               (/ (voc4-detection-width (first agent)) 2)
                               (/ (voc4-detection-width (first patient)) 2))
                            (/ close 2))))
        (close-f (lambda (agent patient)
                  (< (- (abs (- (x (voc4-detection-center (first agent)))
                                (x (voc4-detection-center (first patient)))))
                        (/ (voc4-detection-width (first agent)) 2)
                        (/ (voc4-detection-width (first patient)) 2))
                     close)))
        (stationary-f
         (lambda (agent transformations scale)
          (if (first transformations)
              (begin
               (<= (magnitude
		   (average-flow-in-box (voc4-scale-abs (first agent) scale)
					(first transformations)))
		  stationary))
              #t)))
        (closing-f (lambda (agent patient transformations scale)
                    (> (abs (- (x (voc4-detection-center (first agent)))
                               (x (voc4-detection-center (first patient)))))
                       (+ (abs (- (x (voc4-detection-center
                                      (forward-project-box-scaled
                                       (first agent) (first transformations) 1 scale)))
                                  (x (voc4-detection-center (first patient)))))
                          closing-delta))))
        (departing-f (lambda (agent patient transformations scale)
                      (< (abs (- (x (voc4-detection-center (first agent)))
                                 (x (voc4-detection-center (first patient)))))
                         (+ (abs (- (x (voc4-detection-center
                                        (forward-project-box-scaled
                                         (first agent) (first transformations) 1 scale)))
                                    (x (voc4-detection-center (first patient)))))
                            closing-delta))))
        (moving-direction-f
         (lambda (agent transformations scale direction)
          (if (first transformations)
              (and (< (angle-separation
		       (orientation (average-flow-in-box (voc4-scale-abs (first agent) scale)
							 (first transformations)))
		       direction)
		      angle-delta)
		   (> (magnitude
		       (average-flow-in-box (voc4-scale-abs (first agent) scale)
					    (first transformations)))
		      stationary))
              #t)))
        (moving-together
         (lambda (scale transformations agent patient . _)
          (if (first transformations)
              (and (< (angle-separation
		       (orientation (average-flow-in-box
				     (voc4-scale-abs (first agent) scale)
				     (first transformations)))
		       (orientation (average-flow-in-box
				     (voc4-scale-abs (first patient) scale)
				     (first transformations))))
		      angle-delta)
		   (> (magnitude
		       (average-flow-in-box (voc4-scale-abs (first agent) scale)
					    (first transformations)))
		      stationary)
		   (> (magnitude
		       (average-flow-in-box (voc4-scale-abs (first patient) scale)
					    (first transformations)))
		      stationary))
              #t)))
        (is-object-class-f
         (lambda (class) (lambda (scale transformations agent . _)
			  (or (equal? (voc4-detection-model (first agent)) class)
			      (equal? (voc4-detection-model (first agent)) "padding")))))
        (has-color-f
         (lambda (color) (lambda (scale transformations agent . _)
			  (< (angle-separation
			      (x (hsv-degrees->hsv-radians
				  (unpack-color (voc4-detection-color (first agent)))))
			      color)
			     color-delta))))
        (just-stationary
         (lambda (scale transformations agent patient . _)
          (and (stationary-f agent transformations scale)
	       (stationary-f patient transformations scale))))
        (stationary-but-far
         (lambda (scale transformations agent patient . _)
          (and (far-f agent patient)
	       (stationary-f agent transformations scale)
	       (stationary-f patient transformations scale))))
        (stationary-but-close
         (lambda (scale transformations agent patient . _)
          (and (close-f agent patient)
	       (stationary-f agent transformations scale)
	       (stationary-f patient transformations scale))))
        (overlapping
         (lambda (scale transformations agent patient . _)
          (>= (voc4-detection-intersection-area (first agent) (first patient))
	      50)))
        (approaching
         (lambda (scale transformations agent patient . _)
          (and (closing-f agent patient transformations scale)
	       (stationary-f patient transformations scale))))
        (far (lambda (scale transformations agent patient . _) (far-f agent patient)))
        (close (lambda (scale transformations agent patient . _)
                (close-f agent patient)))
        (really-close (lambda (scale transformations agent patient . _)
                       (really-close-f agent patient)))
        (departing
         (lambda (scale transformations agent patient . _)
          (and (departing-f agent patient transformations scale)
	       (stationary-f patient transformations scale))))
        (picking-up
         (lambda (scale transformations agent patient . _)
          (and (stationary-f agent transformations scale)
	       (moving-direction-f patient transformations scale (- half-pi)))))
        (putting-down
         (lambda (scale transformations agent patient . _)
          (and (stationary-f agent transformations scale)
	       (moving-direction-f patient transformations scale half-pi))))
        (carrying
         (lambda (scale transformations agent patient . _)
          (or (and (moving-direction-f agent transformations scale 0)
		   (moving-direction-f patient transformations scale 0))
	      (and (moving-direction-f agent transformations scale pi)
		 (moving-direction-f patient transformations scale pi)))))
        (quickly
         (lambda (scale transformations agent patient . _)
          (> (magnitude (average-flow-in-box (voc4-scale-abs (first agent) scale)
					     (first transformations)))
             quickly-delta)))
        (slowly
         (lambda (scale transformations agent patient . _)
          (< stationary
             (magnitude (average-flow-in-box (voc4-scale-abs (first agent) scale)
					     (first transformations)))
             slowly-delta)))
        (left-of
         (lambda (scale transformations agent patient . _)
          (< (x (voc4-detection-center (first agent)))
             (- (x (voc4-detection-center (first patient))) pp-delta))))
        (right-of
         (lambda (scale transformations agent patient . _)
          (> (x (voc4-detection-center (first agent)))
             (+ (x (voc4-detection-center (first patient))) pp-delta)))))
  (define (lor a b) (lambda args (or (apply a args) (apply b args))))
  (define (land a b) (lambda args (and (apply a args) (apply b args))))
  (list
   (sentence:fsm
    'approached `#(,(lor stationary-but-far (land approaching far))
                   ,approaching
                   ,stationary-but-close))
   ;; (sentence:fsm
   ;;  'approached `#(,stationary-but-far ,approaching ,stationary-but-close))
   (sentence:fsm
    'put-down `#(,stationary-but-close ,putting-down ,stationary-but-close))
   (sentence:fsm
    'picked-up `#(,stationary-but-close ,picking-up ,stationary-but-close))
   (sentence:fsm
    'carried `#(,stationary-but-close ,carrying ,stationary-but-close))
   (sentence:fsm 'rode `#(
                          ,(land stationary-but-close overlapping)
                          ;; ,(lor (land stationary-but-close overlapping)
                          ;;       (land moving-together overlapping))
                          ;; ,(land moving-together overlapping)
                          ,(land moving-together overlapping)
                          ,(lor (land moving-together overlapping)
                                (lor stationary-but-close
                                     (lambda (scale transformations agent patient . _)
                                      ((is-object-class-f "padding") scale transformations patient))))))

   ;; nouns
   (sentence:fsm 'person `#(,(is-object-class-f "person")))
   (sentence:fsm 'giraffe `#(,(is-object-class-f "giraffe")))
   (sentence:fsm 'gun `#(,(is-object-class-f "gun")))
   (sentence:fsm 'sign `#(,(is-object-class-f "sign")))
   (sentence:fsm 'car `#(,(is-object-class-f "car")))
   (sentence:fsm 'bicycle `#(,(is-object-class-f "bicycle")))
   (sentence:fsm 'object
                 `#(,(lambda (scale transformations agent . _)
                      (member (voc4-detection-model (first agent))
			 '("padding" "car" "bicycle" "giraffe" "gun" "sign")))))
   ;; adverbs
   (sentence:fsm 'quickly `#(,(const #t) ,quickly ,(const #t)))
   (sentence:fsm 'slowly `#(,(const #t) ,slowly ,(const #t)))
   ;; adjuncts
   (sentence:fsm 'from-the-left `#(,left-of))
   (sentence:fsm 'from-the-right `#(,right-of))
   ;; prepositions
   (sentence:fsm 'to-the-left-of `#(,left-of))
   (sentence:fsm 'to-the-right-of `#(,right-of))
   ;; motion prepositions
   (sentence:fsm 'towards `#(,stationary-but-far ,approaching ,stationary-but-close))
   (sentence:fsm 'away-from `#(,stationary-but-close ,departing ,stationary-but-far))
   ;; adjectives
   (sentence:fsm 'red `#(,(has-color-f (degrees->radians 0))))
   (sentence:fsm 'green `#(,(has-color-f (degrees->radians 120))))
   (sentence:fsm 'yellow `#(,(has-color-f (degrees->radians 60)))))))

(define (type->part-of-speech type)
 (case type
  ((noun) pos-c:noun)
  ((verb) pos-c:verb)
  ((adverb) pos-c:adverb)
  ((adjective) pos-c:adjective)
  ((preposition) pos-c:preposition)
  ((motion-preposition) pos-c:motion-preposition)
  (else (fuck-up))))

(define (sentence:create-hmm name type n-roles states
			     state-feature-distributions
			     transition-matrix initial-costs)
 (make-trained-hmm name '() states 0
		   (psi->model
                    (make-psi name (type->part-of-speech type)
			      #f n-roles
			      state-feature-distributions
			      transition-matrix
			      initial-costs))
		   2
		   type
		   'hand))
;; HMMs
(define *toy-corpus:hmms*
 #f)

(define *306-corpus:models* #f)

(define (*306-corpus:hmms*)
 (begin
  (unless *306-corpus:models*
   (set! *306-corpus:models*
	 (let* ((eps 1e-15)  ;; Garbage output probability
		(nearly-one (- 1 eps))
		(dummy-velocity `(discrete ,@(map-n (const (/ 1.0 5)) 5)))
		(stationary-velocity-magnitude `(discrete 0.5 0.49 0.01 0 0))
		(moving-velocity-magnitude `(discrete ,eps ,(- 0.1 eps) 0.3 0.3 0.3))
		;;
		(dummy-x-distance `(discrete ,@(map-n (const (/ 1.0 3)) 3)))
		(near-x-distance `(discrete 0.5 0.5 0))
		(far-x-distance `(discrete 0 ,eps ,nearly-one))
		;;
		(dummy-x-relative-velocity `(discrete ,@(map-n (const (/ 1.0 5)) 5)))
		(moving-x-relative-velocity `(discrete 0.4 0.1 0 0.1 0.4))
		(stationary-x-relative-velocity `(discrete 0 0.2 0.6 0.2 0))
		;;
		(dummy-distance-derivative `(discrete ,@(map-n (const (/ 1.0 5)) 5)))
		(stay `(discrete 0 ,eps ,(- 1 (* 2 eps)) ,eps 0))
		(towards `(discrete 0.3 0.4 0.3 0 0 ))
		(away `(discrete 0 0 0.3 0.4 0.3))
		;;
		(no-displacement '(discrete 1 0 0 0 0))
		(dummy-displacement `(discrete ,@(map-n (const (/ 1.0 5)) 5)))
		(up-displacement `(discrete 0 0 0 0 1))
		(down-displacement `(discrete 0 0 1 0 0))
		(left-displacement `(discrete 0 1 0 0 0))
		(right-displacement `(discrete 0 0 0 1 0))
		(horizontal-displacement '(discrete 0 0.5 0 0.5 0))
		;;
		(slowly `(discrete 0 ,nearly-one ,eps 0 0))
		(quickly `(discrete 0 0 0 0.5 0.5))
		;;
		(dummy-orientation `(discrete ,@(map-n (const (/ 1.0 4)) 4)))
		;;
		(up `(discrete 0 0 0 1))
		(down `(discrete 0 1 0 0))
		(left `(discrete 1 0 0 0))
		(right `(discrete 0 0 1 0))
		(horizontal `(discrete 0.5 0 0.5 0))
		;;
		(to-the-left-of `(discrete 1 0))
		(to-the-right-of `(discrete 0 1))
		;;
		(dummy-color-h `(discrete ,@(map-n (const (/ 1.0 5)) 5)))
		;;
		(dummy-relative-box-area `(discrete 0.5 0.5))
		(agent-larger-than-patient `(discrete 1 0))
		;;
		(dummy-verb-state `#(,dummy-velocity
				     ,dummy-orientation
				     ,dummy-velocity
				     ,dummy-orientation
				     ,dummy-x-distance
				     ,dummy-relative-box-area))
		;; p of staying in non-garbage states
		(in 0.99)
		(out (- 1 in))
		(object-number (+ 1 (length *306-corpus:objects*))))
	  (list
	   ;; verbs
	   ;; features used in verb-features are
	   ;;  1. agent velocity magnitude
	   ;;  2. agent velocity orientation
	   ;;  3. agent location displacement
	   ;;  4. patient velocity magnitude
	   ;;  5. patient velocity orientation
	   ;;  6. patient location displacement
	   ;;  7. agent-patient-x-distance
	   ;;  8. agent-patient-x-relative-velocity
	   ;;  9. agent-larger-than-patient
	   (sentence:create-hmm
	    'approached 'verb 2 3
	    `#(#(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,far-x-distance
		 ,dummy-relative-box-area)
	       #(,moving-velocity-magnitude
		 ,horizontal
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,dummy-x-distance
		 ,dummy-relative-box-area)
	       #(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,near-x-distance
		 ,dummy-relative-box-area))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'put-down 'verb 2 3
	    `#(#(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,near-x-distance
		 ,agent-larger-than-patient)
	       #(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,moving-velocity-magnitude
		 ,down
		 ,near-x-distance
		 ,agent-larger-than-patient)
	       #(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,near-x-distance
		 ,agent-larger-than-patient))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'picked-up 'verb 2 3
	    `#(#(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,near-x-distance
		 ,agent-larger-than-patient)
	       #(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,moving-velocity-magnitude
		 ,up
		 ,near-x-distance
		 ,agent-larger-than-patient)
	       #(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,near-x-distance
		 ,agent-larger-than-patient))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'carried 'verb 2 3
	    `#(#(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,near-x-distance
		 ,agent-larger-than-patient)
	       #(,moving-velocity-magnitude
		 ,horizontal
		 ,moving-velocity-magnitude
		 ,horizontal
		 ,dummy-x-distance
		 ,agent-larger-than-patient)
	       #(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,near-x-distance
		 ,agent-larger-than-patient))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'no-verb 'verb 2 1
	    `#(,dummy-verb-state)
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'person 'noun 1 1
	    `#(#(,(cons
		   'discrete
		   (list-replace (map-n (lambda (_) 0) object-number)
				 (position "person" *306-corpus:objects*) 1))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'giraffe 'noun 1 1
	    `#(#(,(cons
		   'discrete
		   (list-replace (map-n (lambda (_) 0) object-number)
				 (position "giraffe" *306-corpus:objects*) 1))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'gun 'noun 1 1
	    `#(#(,(cons
		   'discrete
		   (list-replace (map-n (lambda (_) 0) object-number)
				 (position "gun" *306-corpus:objects*) 1))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'sign 'noun 1 1
	    `#(#(,(cons
		   'discrete
		   (list-replace (map-n (lambda (_) 0) object-number)
				 (position "sign" *306-corpus:objects*) 1))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'object 'noun 1 1
	    `#(#(,(cons 'discrete
			(map-n (lambda (_) (/ object-number)) object-number))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'no-noun 'noun 1 1
	    `#(#(,(cons 'discrete
			(map-n (lambda (_) (/ object-number)) object-number))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'quickly 'adverb 1 3
	    `#(#(,stationary-velocity-magnitude)
	       #(,quickly)
	       #(,stationary-velocity-magnitude))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'slowly 'adverb 1 3
	    `#(#(,stationary-velocity-magnitude)
	       #(,slowly)
	       #(,stationary-velocity-magnitude))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'no-adverb 'adverb 1 1
	    `#(#(,dummy-velocity))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'to-the-left-of 'preposition 2 1
	    `#(#(,to-the-left-of))
	    `#(#(1))
	    '#(1))
	   (sentence:create-hmm
	    'to-the-right-of 'preposition 2 1
	    `#(#(,to-the-right-of))
	    `#(#(1))
	    '#(1))
	   (sentence:create-hmm
	    'no-preposition 'preposition 2 1
	    `#(#(,dummy-orientation))
	    `#(#(1)) '#(1))
	   ;; motion prepositions
	   (sentence:create-hmm
	    'away-from 'motion-preposition 2 3
	    `#(#(,stay
		 ,near-x-distance)
	       #(,away
		 ,dummy-x-distance)
	       #(,stay
		 ,far-x-distance))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'towards 'motion-preposition 2 3
	    `#(#(,stay
		 ,far-x-distance)
	       #(,towards
		 ,dummy-x-distance)
	       #(,stay
		 ,near-x-distance))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'no-motion-preposition 'motion-preposition 2 1
	    `#(#(,dummy-distance-derivative))
	    `#(#(1)) '#(1))
	   ;; The bins are (red green yellow orange blue) in order
	   (sentence:create-hmm
	    'red 'adjective 1 1
	    `#(#((discrete 1 0 0 0 0)))
	    '#(#(1)) '#(1))
	   (sentence:create-hmm
	    'green 'adjective 1 1
	    `#(#((discrete 0 1 0 0 0)))
	    '#(#(1)) '#(1))
	   (sentence:create-hmm
	    'yellow 'adjective 1 1
	    `#(#((discrete 0 0 1 0 0)))
	    '#(#(1)) '#(1))
	   (sentence:create-hmm
	    'no-adjective 'adjective 1 1
	    `#(#(,dummy-color-h))
	    `#(#(1)) '#(1))
	   ))))
  *306-corpus:models*))

(define *new3-corpus:models* #f)

(define (*new3-corpus:hmms*)
 (begin
  (unless *new3-corpus:models*
   (set! *new3-corpus:models*
	 (let* ((eps 1e-30)  ;; Garbage output probability
		(nearly-one (- 1 eps))
		(dummy-velocity `(discrete ,@(map-n (const (/ 5)) 5)))
		(stationary-velocity-magnitude `(discrete 0.5 0.49 0.01 0 0))
		(moving-velocity-magnitude `(discrete ,eps ,(- 0.1 eps) 0.3 0.3 0.3))
		;;
		(dummy-x-distance `(discrete ,@(map-n (const (/ 3)) 3)))
		(close-x-distance `(discrete 1 0 0))
		(near-x-distance `(discrete 0.5 0.5 0))
		(far-x-distance `(discrete 0 0 1))
		(not-close-x-distance `(discrete 0 0.5 0.5))
		;;
		(not-quickly `(discrete ,(- 0.25 eps) 0.25 0.25 0.25 ,eps))
		(slowly `(discrete 0 0.3 0.4 0.3 0))
		(quickly `(discrete 0 0 0 0 1))
		;;
		(dummy-orientation `(discrete ,@(map-n (const (/ 4)) 4)))
		;;
		(up `(discrete 0 0 0 1))
		(down `(discrete 0 1 0 0))
		(left `(discrete 1 0 0 0))
		(right `(discrete 0 0 1 0))
		(horizontal `(discrete 0.5 0 0.5 0))
		;;
		(to-the-left-of `(discrete 1 0 0))
		(to-the-right-of `(discrete 0 1 0))
		;;
		(dummy-color-h `(discrete ,@(map-n (const (/ 5)) 5)))
		;;
		(dummy-relative-box-area `(discrete 0.5 0.5))
		(agent-larger-than-patient `(discrete 1 0))
		;; p of staying in non-garbage states
		(in 0.99)
		(out (- 1 in))
		(object-number (length *new3-corpus:objects*)))
	  (list
	   ;; verbs
	   ;; features used in verb-features are
	   ;;  1. agent velocity magnitude
	   ;;  2. agent velocity orientation
	   ;;  3. patient velocity magnitude
	   ;;  4. patient velocity orientation
	   ;;  5. agent-patient-x-distance
	   ;;  6. agent-larger-than-patient
	   (sentence:create-hmm
	    'approached 'verb 2 3
	    `#(#(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,far-x-distance
		 ,dummy-relative-box-area)
	       #(,moving-velocity-magnitude
		 ,horizontal
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,dummy-x-distance
		 ,dummy-relative-box-area)
	       #(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,close-x-distance
		 ,dummy-relative-box-area))
	    `#(#(0.001 0.999 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'put-down 'verb 2 3
	    `#(#(,dummy-velocity
		 ,dummy-orientation
		 ,dummy-velocity
		 ,dummy-orientation
		 ,close-x-distance
		 ,agent-larger-than-patient)
	       #(,dummy-velocity
		 ,dummy-orientation
		 ,moving-velocity-magnitude
		 ,down
		 ,close-x-distance
		 ,dummy-relative-box-area)
	       #(,dummy-velocity
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,close-x-distance
		 ,agent-larger-than-patient)
	       )
	    `#(#(0.001 0.999 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    `#(1 0 0))
	   (sentence:create-hmm
	    'picked-up 'verb 2 3
	    `#(#(,dummy-velocity
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,close-x-distance
		 ,agent-larger-than-patient)
	       #(,dummy-velocity
		 ,dummy-orientation
		 ,moving-velocity-magnitude
		 ,up
		 ,close-x-distance
		 ,dummy-relative-box-area)
	       #(,dummy-velocity
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,close-x-distance
		 ,agent-larger-than-patient))
	    `#(#(0.001 0.999 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'carried 'verb 2 3
	    `#(#(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,close-x-distance
		 ,agent-larger-than-patient)
	       #(,moving-velocity-magnitude
		 ,horizontal
		 ,moving-velocity-magnitude
		 ,horizontal
		 ,close-x-distance
		 ,agent-larger-than-patient)
	       #(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,close-x-distance
		 ,agent-larger-than-patient)
	       )
	    `#(#(0.001 0.999 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'person 'noun 1 1
	    `#(#(,(cons
		   'discrete
		   (list-replace (map-n (lambda (_) 0) object-number)
				 (position "person" *new3-corpus:objects*) 1))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'chair 'noun 1 1
	    `#(#(,(cons
		   'discrete
		   (list-replace (map-n (lambda (_) 0) object-number)
				 (position "chair" *new3-corpus:objects*) 1))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'backpack 'noun 1 1
	    `#(#(,(cons
		   'discrete
		   (list-replace (map-n (lambda (_) 0) object-number)
				 (position "backpack" *new3-corpus:objects*) 1))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'trash-can 'noun 1 1
	    `#(#(,(cons
		   'discrete
		   (list-replace (map-n (lambda (_) 0) object-number)
				 (position "trash-can" *new3-corpus:objects*) 1))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'object 'noun 1 1
	    `#(#(,(cons 'discrete
			(map-n (lambda (_) (/ object-number)) object-number))))
	    `#(#(1)) '#(1))
	   ;;adverbs
	   (sentence:create-hmm
	    'quickly 'adverb 1 3
	    `#(#(,not-quickly)
	       #(,quickly)
	       #(,not-quickly))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'slowly 'adverb 1 3
	    `#(#(,stationary-velocity-magnitude)
	       #(,slowly)
	       #(,stationary-velocity-magnitude))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   ;; prepositions
	   (sentence:create-hmm
	    'to-the-left-of 'preposition 2 1
	    `#(#(,to-the-left-of))
	    `#(#(1))
	    '#(1))
	   (sentence:create-hmm
	    'to-the-right-of 'preposition 2 1
	    `#(#(,to-the-right-of))
	    `#(#(1))
	    '#(1))
	   ;; motion prepositions
	   (sentence:create-hmm
	    'away-from 'motion-preposition 2 3
	    `#(#(,stationary-velocity-magnitude
		 ,close-x-distance)
	       #(,moving-velocity-magnitude
		 ,dummy-x-distance)
	       #(,stationary-velocity-magnitude
		 ,far-x-distance))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'towards 'motion-preposition 2 3
	    `#(#(,stationary-velocity-magnitude
		 ,far-x-distance)
	       #(,moving-velocity-magnitude
		 ,dummy-x-distance)
	       #(,stationary-velocity-magnitude
		 ,close-x-distance))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   ;; adjectives
	   ;; (red green yellow orange blue)
	   (sentence:create-hmm
	    'red 'adjective 1 1
	    `#(#((discrete 1 0 0 0 0)))
	    '#(#(1)) '#(1))
	   (sentence:create-hmm
	    'blue 'adjective 1 1
	    `#(#((discrete 0 0 0 0 1)))
	    '#(#(1)) '#(1))
	   (sentence:create-hmm
	    'green 'adjective 1 1
	    `#(#((discrete 0 1 0 0 0)))
	    '#(#(1)) '#(1))
	   (sentence:create-hmm
	    'orange 'adjective 1 1
	    `#(#((discrete 0 0 0 1 0)))
	    '#(#(1)) '#(1))
	   (sentence:create-hmm
	    'yellow 'adjective 1 1
	    `#(#((discrete 0 0 1 0 0)))
	    '#(#(1)) '#(1))))))
  *new3-corpus:models*))

(define *new4-corpus:models* #f)

(define (*new4-corpus:hmms*)
 (begin
  (unless *new4-corpus:models*
   (set! *new4-corpus:models*
	 (let* ((eps 1e-30)  ;; Garbage output probability
		(nearly-one (- 1 eps))
		(dummy-velocity `(discrete ,@(map-n (const (/ 5)) 5)))
		(stationary-velocity-magnitude `(discrete 0.5 0.49 0.01 0 0))
		(moving-velocity-magnitude `(discrete ,eps ,(- 0.1 eps) 0.3 0.3 0.3))
		(dummy-x-distance `(discrete ,@(map-n (const (/ 3)) 3)))
		(close-x-distance `(discrete 1 0 0))
		(far-x-distance `(discrete 0 0 1))
		(dummy-y-distance `(discrete ,@(map-n (const (/ 3)) 3)))
		(close-y-distance `(discrete 1 0 0))
		(not-close-y-distance `(discrete 0 0.5 0.5))
		(far-y-distance `(discrete 0 0 1))
		;;
		(not-quickly `(discrete ,(- 0.25 eps) 0.25 0.25 0.25 ,eps))
		(slowly `(discrete 0 0.3 0.4 0.3 0))
		(quickly `(discrete 0 0 0 0.1 0.9))
		;;
		(dummy-orientation `(discrete ,@(map-n (const (/ 4)) 4)))
		(up `(discrete 0 0 0 1))
		(down `(discrete 0 1 0 0))
		(left `(discrete 1 0 0 0))
		(right `(discrete 0 0 1 0))
		(horizontal `(discrete 0.5 0 0.5 0))
		;;
		(to-the-left-of `(discrete 1 0 0))
		(to-the-right-of `(discrete 0 1 0))
		;;
		(dummy-color-h `(discrete ,@(map-n (const (/ 5)) 5)))
		;;
		(dummy-relative-box-area `(discrete 0.5 0.5))
		(agent-larger-than-patient `(discrete 1 0))
		(agent-smaller-than-patient `(discrete 0 1))
		;;
		(overlap-much `(discrete 0 1))
		(overlap-little `(discrete 1 0))
		(dummy-overlap `(discrete 0.5 0.5))
		;;
		(dummy-verb-state
		 `#(,dummy-velocity
		    ,dummy-orientation
		    ,dummy-velocity
		    ,dummy-orientation
		    ,dummy-x-distance
		    ,dummy-relative-box-area))
		(dummy-verb2-state
		 `#(,dummy-velocity
		    ,dummy-orientation
		    ,dummy-velocity
		    ,dummy-orientation
		    ,dummy-velocity
		    ,dummy-orientation
		    ,dummy-x-distance
		    ,dummy-y-distance
		    ,dummy-relative-box-area
		    ,dummy-overlap
		    ,dummy-x-distance
		    ,dummy-y-distance
		    ,dummy-relative-box-area
		    ,dummy-overlap
		    ,dummy-x-distance
		    ,dummy-y-distance
		    ,dummy-relative-box-area
		    ,dummy-overlap))
		;; p of staying in non-garbage states
		(in 0.99)
		(out (- 1 in))
		(object-number (length *new4-corpus:objects*)))
	  (list
	   ;; verbs
	   ;; features used in verb-features are
	   ;;  1. agent velocity magnitude
	   ;;  2. agent velocity orientation
	   ;;  3. agent location displacement
	   ;;  4. patient velocity magnitude
	   ;;  5. patient velocity orientation
	   ;;  6. patient location displacement
	   ;;  7. agent-patient-x-distance
	   ;;  8. agent-patient-x-relative-velocity
	   ;;  9. agent-larger-than-patient
	   (sentence:create-hmm
	    'approached 'verb 2 3
	    `#(#(,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,far-x-distance
	       	 ,dummy-relative-box-area)
	       #(,moving-velocity-magnitude
	       	 ,horizontal
	       	 ,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,dummy-x-distance
	       	 ,dummy-relative-box-area)
	       #(,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,close-x-distance
	       	 ,dummy-relative-box-area))
	    `#(#(0.001 0.999 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'left 'verb 2 3
	    `#(,dummy-verb-state
	       #(,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,close-x-distance
	       	 ,dummy-relative-box-area)
	       #(,moving-velocity-magnitude
	       	 ,horizontal
	       	 ,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,dummy-x-distance
	       	 ,dummy-relative-box-area)
	       #(,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,far-x-distance
	       	 ,dummy-relative-box-area))
	    `#(#(0.001 0.999 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'put-down 'verb 2 3
	    `#(#(,dummy-velocity
		 ,dummy-orientation
		 ,dummy-velocity
		 ,dummy-orientation
		 ,close-x-distance
		 ,agent-larger-than-patient)
	       #(,dummy-velocity
		 ,dummy-orientation
		 ,moving-velocity-magnitude
		 ,down
		 ,close-x-distance
		 ,agent-larger-than-patient)
	       #(,dummy-velocity
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,close-x-distance
		 ,agent-larger-than-patient))
	    `#(#(0.001 0.999 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    `#(1 0 0))
	   (sentence:create-hmm
	    'picked-up 'verb 2 3
	    `#(#(,dummy-velocity
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,close-x-distance
		 ,agent-larger-than-patient)
	       #(,dummy-velocity
		 ,dummy-orientation
		 ,moving-velocity-magnitude
		 ,up
		 ,close-x-distance
		 ,agent-larger-than-patient)
	       #(,dummy-velocity
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,close-x-distance
		 ,agent-larger-than-patient))
	    `#(#(0.001 0.999 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'carried 'verb 2 3
	    `#(#(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,close-x-distance
		 ,agent-larger-than-patient)
	       #(,moving-velocity-magnitude
		 ,horizontal
		 ,moving-velocity-magnitude
		 ,horizontal
		 ,close-x-distance
		 ,agent-larger-than-patient)
	       #(,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,stationary-velocity-magnitude
		 ,dummy-orientation
		 ,close-x-distance
		 ,agent-larger-than-patient))
	    `#(#(0.001 0.999 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   ;; verb with three participants
	   (sentence:create-hmm
	    'gave 'verb 3 3
	    `#(#(,stationary-velocity-magnitude
	   	 ,dummy-orientation
	   	 ,stationary-velocity-magnitude
	   	 ,dummy-orientation
	   	 ,stationary-velocity-magnitude
	   	 ,dummy-orientation
	   	 ;; 0 1
	   	 ,close-x-distance
	   	 ,close-y-distance
	   	 ,agent-larger-than-patient
	   	 ,overlap-much
	   	 ;; 0 2
	   	 ,dummy-x-distance
	   	 ,close-y-distance
	   	 ,dummy-relative-box-area
	   	 ,overlap-little
	   	 ;; 1 2
	   	 ,dummy-x-distance
	   	 ,close-y-distance
	   	 ,agent-smaller-than-patient
	   	 ,overlap-little)
	       #(,stationary-velocity-magnitude
	   	 ,dummy-orientation
	   	 ,moving-velocity-magnitude
	   	 ,dummy-orientation
	   	 ,stationary-velocity-magnitude
	   	 ,dummy-orientation
	   	 ;; 0 1
	   	 ,dummy-x-distance
	   	 ,close-y-distance
	   	 ,agent-larger-than-patient
	   	 ,dummy-overlap
	   	 ;; 0 2
	   	 ,dummy-x-distance
	   	 ,close-y-distance
	   	 ,dummy-relative-box-area
	   	 ,overlap-little
	   	 ;; 1 2
	   	 ,dummy-x-distance
	   	 ,close-y-distance
	   	 ,agent-smaller-than-patient
	   	 ,dummy-overlap)
	       #(,stationary-velocity-magnitude
	   	 ,dummy-orientation
	   	 ,stationary-velocity-magnitude
	   	 ,dummy-orientation
	   	 ,stationary-velocity-magnitude
	   	 ,dummy-orientation
	   	 ;; 0 1
	   	 ,dummy-x-distance
	   	 ,close-y-distance
	   	 ,agent-larger-than-patient
	   	 ,overlap-little
	   	 ;; 0 2
	   	 ,dummy-x-distance
	   	 ,close-y-distance
	   	 ,dummy-relative-box-area
	   	 ,overlap-little
	   	 ;; 1 2
		 ,close-x-distance
	   	 ,close-y-distance
	   	 ,agent-smaller-than-patient
	   	 ,overlap-much))
	    `#(#(0.001 0.999 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    `#(1 0 0))
	   (sentence:create-hmm
	    'replaced 'verb 3 3
	    `#(#(,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ;; 0 1
	       	 ,close-x-distance
	       	 ,not-close-y-distance
	       	 ,agent-larger-than-patient
	       	 ,dummy-overlap
	       	 ;; 0 2
	       	 ,close-x-distance
	       	 ,close-y-distance
	       	 ,agent-larger-than-patient
	       	 ,overlap-much
	       	 ;; 1 2
	       	 ,close-x-distance
	       	 ,not-close-y-distance
	       	 ,dummy-relative-box-area
	       	 ,overlap-little)
	       #(,dummy-velocity
	       	 ,dummy-orientation
	       	 ,dummy-velocity
	       	 ,dummy-orientation
	       	 ,dummy-velocity
	       	 ,dummy-orientation
	       	 ;; 0 1
	       	 ,close-x-distance
	       	 ,dummy-y-distance
	       	 ,agent-larger-than-patient
	       	 ,dummy-overlap
	       	 ;; 0 2
	       	 ,dummy-x-distance
	       	 ,dummy-y-distance
	       	 ,agent-larger-than-patient
	       	 ,dummy-overlap
	       	 ;; 1 2
	       	 ,close-x-distance
	       	 ,dummy-y-distance
	       	 ,dummy-relative-box-area
	       	 ,dummy-overlap
	       	 )
	       #(,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ;; 0 1
	       	 ,close-x-distance
	       	 ,close-y-distance
	       	 ,agent-larger-than-patient
	       	 ,overlap-much
	       	 ;; 0 2
	       	 ,dummy-x-distance
	       	 ,not-close-y-distance
	       	 ,agent-larger-than-patient
	       	 ,dummy-overlap
	       	 ;; 1 2
	       	 ,dummy-x-distance
	       	 ,not-close-y-distance
	       	 ,dummy-relative-box-area
	       	 ,overlap-little))
	    `#(#(0.001 0.999 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    `#(1 0 0))
	   (sentence:create-hmm
	    'collided-with 'verb 2 3
	    `#(#(,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,far-x-distance
	       	 ,dummy-relative-box-area)
	       #(,moving-velocity-magnitude
	       	 ,horizontal
	       	 ,moving-velocity-magnitude
	       	 ,horizontal
	       	 ,dummy-x-distance
	       	 ,dummy-relative-box-area)
	       #(,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,stationary-velocity-magnitude
	       	 ,dummy-orientation
	       	 ,close-x-distance
	       	 ,dummy-relative-box-area))
	    `#(#(0.001 0.999 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    `#(1 0 0))
	   ;; nouns
	   (sentence:create-hmm
	    'person 'noun 1 1
	    `#(#(,(cons
		   'discrete
		   (list-replace (map-n (lambda (_) 0) object-number)
				 (position "person" *new4-corpus:objects*) 1))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'chair 'noun 1 1
	    `#(#(,(cons
		   'discrete
		   (list-replace (map-n (lambda (_) 0) object-number)
				 (position "chair" *new4-corpus:objects*) 1))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'backpack 'noun 1 1
	    `#(#(,(cons
		   'discrete
		   (list-replace (map-n (lambda (_) 0) object-number)
				 (position "backpack" *new4-corpus:objects*) 1))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'trash-can 'noun 1 1
	    `#(#(,(cons
		   'discrete
		   (list-replace (map-n (lambda (_) 0) object-number)
				 (position "trash-can" *new4-corpus:objects*) 1))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'stool 'noun 1 1
	    `#(#(,(cons
		   'discrete
		   (list-replace (map-n (lambda (_) 0) object-number)
				 (position "stool" *new4-corpus:objects*) 1))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'traffic-cone 'noun 1 1
	    `#(#(,(cons
		   'discrete
		   (list-replace (map-n (lambda (_) 0) object-number)
				 (position "traffic-cone" *new4-corpus:objects*) 1))))
	    `#(#(1)) '#(1))
	   (sentence:create-hmm
	    'object 'noun 1 1
	    `#(#(,(cons 'discrete
			(map-n (lambda (_) (/ object-number)) object-number))))
	    `#(#(1)) '#(1))
	   ;;adverbs
	   (sentence:create-hmm
	    'quickly 'adverb 1 3
	    `#(#(,not-quickly)
	       #(,quickly)
	       #(,not-quickly))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'slowly 'adverb 1 3
	    `#(#(,stationary-velocity-magnitude)
	       #(,slowly)
	       #(,stationary-velocity-magnitude))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   ;; prepositions
	   (sentence:create-hmm
	    'to-the-left-of 'preposition 2 1
	    `#(#(,to-the-left-of))
	    `#(#(1))
	    '#(1))
	   (sentence:create-hmm
	    'to-the-right-of 'preposition 2 1
	    `#(#(,to-the-right-of))
	    `#(#(1))
	    '#(1))
	   ;; motion prepositions
	   (sentence:create-hmm
	    'away-from 'motion-preposition 2 3
	    `#(#(,stationary-velocity-magnitude
		 ,close-x-distance)
	       #(,moving-velocity-magnitude
		 ,dummy-x-distance)
	       #(,stationary-velocity-magnitude
		 ,far-x-distance))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   (sentence:create-hmm
	    'towards 'motion-preposition 2 3
	    `#(#(,stationary-velocity-magnitude
		 ,far-x-distance)
	       #(,moving-velocity-magnitude
		 ,dummy-x-distance)
	       #(,stationary-velocity-magnitude
		 ,close-x-distance))
	    `#(#(0.01 0.99 0.00)
	       #(0.00 ,in  ,out)
	       #(0.00 0.00 1.00))
	    '#(1 0 0))
	   ;; adjectives
	   ;; (red green yellow orange blue)
	   (sentence:create-hmm
	    'red 'adjective 1 1
	    `#(#((discrete 1 0 0 0 0)))
	    '#(#(1)) '#(1))
	   (sentence:create-hmm
	    'blue 'adjective 1 1
	    `#(#((discrete 0 0 0 0 1)))
	    '#(#(1)) '#(1))
	   (sentence:create-hmm
	    'green 'adjective 1 1
	    `#(#((discrete 0 1 0 0 0)))
	    '#(#(1)) '#(1))
	   (sentence:create-hmm
	    'orange 'adjective 1 1
	    `#(#((discrete 0 0 0 1 0)))
	    '#(#(1)) '#(1))
	   (sentence:create-hmm
	    'yellow 'adjective 1 1
	    `#(#((discrete 0 0 1 0 0)))
	    '#(#(1)) '#(1))))))
  *new4-corpus:models*))

(define *306-corpus:feature-medoids*
 '((velocity
    ((0.0558392865914896 0.03611704661136736 2358)
     (0.452623613500469 0.2398938853666999 716)
     (2.048151936719128 0.6698442618326905 386)
     (5.005485140046349 0.9811639049363998 281)
     (11.07450953642866 4.132036590815614 211))
    0)
   (orientation
    ((3.141592653589793 0 0)
     ;;  (2.356194490192345 0 0)
     (1.570796326794897 0 0)
     ;;  (0.7853981633974483 0 0)
     (0 0 0)
     ;;  (-0.7853981633974483 0 0)
     (-1.570796326794897 0 0)
     ;;  (-2.356194490192345 0 0)
     )
    1)
   (distance
    ((193.320347826087 60.51595537981039 2875)
     (411.5739675901725 43.69259976496725 1913)
     (633.2763157894736 75.17305158547319 1140))
    0)
   (distance-derivative
    ((-566.9215686274509 120.6410724352354 102)
     (-20.17375886524823 14.40639704929215 1128)
     (-0.02193362193362193 1.185814792818282 3465)
     (20.73809523809524 15.84629695657883 1134)
     (604.7777777777778 68.08989238915552 99))
    0)
   (displacement
    ((50 1 0)
     (150 1 0))
    0)
   (box-ratio
    ((0.5 1 0)
     (2.1 1 0))
    0)
   (color
    ((0. 0 0)
     (2.094395102393195 0 0)
     (1.047197551196598 0 0)
     (0.5235987755982988 0 0)
     (4.188790204786391 0 0))
    1)))

(define *new3-corpus:feature-medoids*
 '((velocity
    ((0.0558392865914896 0.03611704661136736 0)
     (0.452623613500469 0.2398938853666999 0)
     (2.048151936719128 0.6698442618326905 0)
     (5.005485140046349 0.9811639049363998 0)
     (10 3 0))
    0)
   (orientation
    ((3.141592653589793 0 0)
     ;;  (2.356194490192345 0 0)
     (1.570796326794897 0 0)
     ;;  (0.7853981633974483 0 0)
     (0 0 0)
     ;;  (-0.7853981633974483 0 0)
     (-1.570796326794897 0 0)
     ;;  (-2.356194490192345 0 0)
     )
    1)
   (x-distance
    ((193.320347826087 1 2875)
     (300.2763157894736 1 1140)
     (411.5739675901725 1 1913))
    0)
   (distance-derivative
    ((-561.5195530726257 210.168443771519 179)
     (-17.19065374098931 15.99299785089329 4023)
     (-0.004427306483861753 0.4325156667509216 14004)
     (16.35248857898533 13.92810561167888 4159)
     (509.5628415300546 222.5936412188169 183))
    0)
   (displacement
    ((50 1 0)
     (200 1 0))
    0)
   (box-ratio
    ((0.5 1 0)
     (2.1 1 0))
    0)
   (color
    ((0. 0 0)   ;;  0 degrees
     (2.094395102393195 0 0)  ;; 120 degrees
     (1.047197551196598 0 0)  ;; 60 degrees
     (0.5235987755982988 0 0) ;; 30 degrees
     (4.188790204786391 0 0)) ;; 240 degrees
    1)
   ))

(define *new4-corpus:feature-medoids*
 '((velocity
    ((0.0558392865914896 0.0361170466113673 0)
     (1.452623613500469 0.2398938853666999 0)
     (4.048151936719128 0.6698442618326905 0)
     (7.005485140046349 0.9811639049363998 0)
     (13 3 0))
    0)
   (orientation
    ((3.141592653589793 0 0)
     (1.570796326794897 0 0)
     (0 0 0)
     (-1.570796326794897 0 0)
     )
    1)
   ;;  dimensions of 1280x960
   (x-distance
    ((200.320347826087 1 2875)
     (250.2763157894736 1 1140)
     (500.5739675901725 1 1913))
    0)
   (y-distance
    ((50.320347826087 1 2875)
     (100.2763157894736 1 1140)
     (300.5739675901725 1 1913))
    0)
   (distance-derivative
    ((-561.5195530726257 210.168443771519 179)
     (-17.19065374098931 15.99299785089329 4023)
     (-0.004427306483861753 0.4325156667509216 14004)
     (16.35248857898533 13.92810561167888 4159)
     (509.5628415300546 222.5936412188169 183))
    0)
   (box-ratio
    ((0.5 1 0)
     (2.1 1 0))
    0)
   (color
    ((0. 0 0)   ;;  0 degrees
     (2.094395102393195 0 0)  ;; 120 degrees
     (1.047197551196598 0 0)  ;; 60 degrees
     (0.5235987755982988 0 0) ;; 30 degrees
     (4.188790204786391 0 0)) ;; 240 degrees
    1)
   (overlap
    ((0.2 1 0)
     (0.4 1 0))
    0)))

(define (feature-medoid->c-feature-medoid medoid)
 (let* ((name (symbol->c-string (first medoid)))
	(id (third medoid))
	(mean (map first (second medoid)))
	(sigma (map second (second medoid)))
	(nn (length mean))
	(fm (allocate-feature-medoid nn)))
  (set-feature-medoid-name! fm name)
  (set-feature-medoid-dm-id! fm id)
  (for-each-indexed (lambda (x i)
		     (set-feature-medoid-mean! fm i x)) mean)
  (for-each-indexed (lambda (x i)
		     (set-feature-medoid-sigma! fm i x)) sigma)
  fm))
