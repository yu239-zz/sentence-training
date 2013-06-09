/* LaHaShem HaAretz U'Mloah */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <math.h>
#include "hmm.h"
#include "hmm-data.h"
#include "hmm-features.h"

#define CLIP(x, t, u) \
  if (x < t) x = t;    \
  if (x > u) x = u;

Flow *allocFlow(int ww, int hh){
  Flow *fl = safe_malloc(sizeof *fl);
  fl->ww = ww;
  fl->hh = hh;
  fl->flow = safe_malloc(ww * hh * 2 * sizeof *(fl->flow));
  return fl;
}

void freeFlow(Flow *fl){
  if (fl){
    if (fl->flow){
      free(fl->flow);
    }
    free(fl);
  }
}

void set_flow(Flow *fl, Real *flow, int ww, int hh){
  assert(fl && fl->flow && flow);
  assert(fl->ww == ww && fl->hh == hh);
  memcpy(fl->flow, flow, ww * hh * 2 * sizeof(Real));
}

Real average_flow_x_in_box(Box *d, Flow *fl, Real scale){
  return average_flow_in_box(d, fl, scale, 0);
}

Real average_flow_y_in_box(Box *d, Flow *fl, Real scale){
  return average_flow_in_box(d, fl, scale, 1);
}

/* Can't include idealib-c.h */
Real integral_optical_flow_area(Real *integral_flow,
				const unsigned height,
				const unsigned width,
				unsigned x1, unsigned y1,
				unsigned x2, unsigned y2);

Real average_flow_in_box(Box *d, Flow *fl, Real scale, int flag){
  int x1, y1, x2, y2, area;
  Real integral = 0;
  assert(d && fl);
  /* The box dimension is 1280 x 960
     The videos used are 640 x 480
     The optical flow is 320 x 240
   */
  x1 = (int)round(d->x1 * scale / 2);
  y1 = (int)round(d->y1 * scale / 2);
  x2 = (int)round(d->x2 * scale / 2);
  y2 = (int)round(d->y2 * scale / 2);
  CLIP(x1, 0, fl->ww - 1);
  CLIP(y1, 0, fl->hh - 1);
  CLIP(x2, 0, fl->ww - 1);
  CLIP(y2, 0, fl->hh - 1);
  area = (x2 - x1 + 1) * (y2 - y1 + 1);

  switch(flag){
  case 0:  /* x direction */
    integral = integral_optical_flow_area(fl->flow, fl->hh, fl->ww,
					  x1, y1, x2, y2);
    break;
  case 1:
    integral = integral_optical_flow_area(fl->flow + fl->hh * fl->ww,
					  fl->hh, fl->ww,
					  x1, y1, x2, y2);
    break;
  default: panic("average_flow_in_box: flag = %d unrecognized", flag);
  }
  /* Because we downsize the square by half */
  return integral / area * 4;
}

Box *forward_projected_box_scaled(Box *d, Flow *fl, Real scale){
  Real shift_x = average_flow_x_in_box(d, fl, scale) / scale;
  Real shift_y = average_flow_y_in_box(d, fl, scale) / scale;
  Box *new_d = box_shift(d, shift_x, shift_y);
  return new_d;
}

/* Constants in these two functions are fixed empirically for all detections
   (i.e., the a and b arguments for sigmoid function)
*/
Real box_pair_score(Box *d1, Box *d2, Flow *fl, Real scale){
  Box *forwarded_d = forward_projected_box_scaled(d1, fl, scale);
  Real distance = box_distance(forwarded_d, d2);
  freeBox(forwarded_d);
  return my_log(sigmoid(distance, 50, - 1.0/11));
}

Real box_single_score(Box *d){
  return my_log(sigmoid(d->strength, 0, 6));
}

Real box_similarity(Box *d1, Box *d2){
  if (strcmp(d1->model, d2->model) == 0)
    return 0;
  return NEGATIVE_INFINITY;
}

FeatureMedoid *allocFeatureMedoid(int nn){
  FeatureMedoid *fm = safe_malloc(sizeof *fm);
  fm->nn = nn;
  fm->mean = safe_malloc(nn * sizeof *(fm->mean));
  fm->sigma = safe_malloc(nn * sizeof *(fm->sigma));
  fm->name = NULL;
  return fm;
}

void set_feature_medoid_name(FeatureMedoid *fm, char *name){
  assert(fm && name);
  if (fm->name) free(fm->name);
  fm->name = safe_malloc(strlen(name) + 1);
  strcpy(fm->name, name);
}

void set_feature_medoid_mean(FeatureMedoid *fm, int i, Real x){
  assert(fm);
  if (i < 0 || i >= fm->nn)
    panic("setFeatureMedoidMean: i = %d out of range [0, %d]", i, fm->nn - 1);
  fm->mean[i] = x;
}

void set_feature_medoid_sigma(FeatureMedoid *fm, int i, Real x){
  assert(fm);
  if (i < 0 || i >= fm->nn)
    panic("setFeatureMedoidSigma: i = %d out of range [0, %d]", i, fm->nn - 1);
  if (x < 0.0)
    panic("setFeatureMedoidSigma: sigma %0.4lf negative", x);
  fm->sigma[i] = x;
}

void set_feature_medoid_dm_id(FeatureMedoid *fm, int id){
  assert(fm);
  if (id != 0 && id != 1)
    panic("set_feature_medoid_dm_id: id = %d unrecognized distance metric id", id);
  fm->dm_id = id;
}

void freeFeatureMedoid(FeatureMedoid *fm){
  if (fm){
    free(fm->mean);
    free(fm->sigma);
    if (fm->name) free(fm->name);
    free(fm);
  }
}

Features allocFeatures(int tt, int ii){
  int t;
  Features f = safe_malloc(sizeof *f);
  f->tt = tt;
  f->ii = ii;
  f->v = safe_malloc(tt * sizeof *(f->v));
  for (t = 0; t < tt; t ++)
    f->v[t] = safe_malloc(ii * sizeof *(f->v[t]));
  return f;
}

void freeFeatures(Features f){
  int t;
  if(f){
    if (f->v){
      for (t = 0; t < f->tt; t ++)
	free(f->v[t]);
      free(f->v);
    }
    free(f);
  }
}

int Features_tt(Features f){
  assert(f);
  return f->tt;
}

int Features_ii(Features f){
  assert(f);
  return f->ii;
}

Real **Features_v(Features f){
  assert(f);
  return f->v;
}

Real get_feature(Features f, int t, int i){
  assert(f);
  if (t < 0 || t >= f->tt)
    panic("get_feature: t = %d out of range [0, %d)", t, f->tt);
  if (i < 0 || i >= f->ii)
    panic("get_feature: i = %d out of range [0, %d)", i, f->ii);
  return f->v[t][i];
}

void print_Features(Features f){
  int t, i;
  assert(f);
  for (t = 0; t < f->tt; t ++){
    for (i = 0; i < f->ii; i ++)
      printf("%0.4lf ", f->v[t][i]);
    printf("\n");
  }
}

int quantize(Real x, char *type, FeatureMedoid **fms, int mn){
  int m, nn;
  double *mean = NULL, *sigma = NULL;
  distance_metric dm = NULL;
  for (m = 0; m < mn; m ++)
    if (strcmp(type, fms[m]->name) == 0)
      break;
  if (m == mn)
    panic("quantize: type = %s unrecognized value type!", type);
  nn = fms[m]->nn;
  mean = fms[m]->mean;
  sigma = fms[m]->sigma;
  dm = (fms[m]->dm_id == 0)? fake_mahalanobis: my_angle_separation;
  return nearest_1d(x, mean, sigma, nn, dm);
}

Real fake_mahalanobis(Real x, Real mean, Real sigma){
  return fabs(x - mean) / sqrt(sigma);
}

Real my_angle_separation(Real x, Real mean, Real sigma){
  return angle_separation(x, mean);
}

Real normalize_rotation(Real rotation){
  if (rotation > PI)
    return normalize_rotation(rotation - 2 * PI);
  else if (rotation <= -PI)
    return normalize_rotation(rotation + 2 * PI);
  else
    return rotation;
}

Real angle_separation(Real x, Real y){
  Real x_y, y_x;
  x_y = x - y;
  y_x = y - x;
  return MIN(fabs(normalize_rotation(x_y)), fabs(normalize_rotation(y_x)));
}

int nearest_1d(Real x, Real *mean, Real *sigma, int nn, distance_metric dm){
  Real minimum = INFINITY, distance;
  int n, m, position = -1;

  for (n = 0; n < nn; n ++){
    distance = dm(x, mean[n], sigma[n]);
    /* Find out whether there is another cluster beween x and mean[n]
       If ture, set the distance from x to mean[n] as INFINITY
     */
    for (m = 0; m < nn; m ++)
      if ((mean[n] < mean[m] && mean[m] < x)
	  || (x < mean[m] && mean[m] < mean[n]))
	break;
    if (dm != my_angle_separation && m < nn)
      distance = INFINITY;
    if (distance < minimum){
      minimum = distance;
      position = n;
    }
  }
  assert(position != -1);
  return position;
}

/* Compute the feature vector for a new frame by calling word_features as a
   special case: the tracks are of length 1 
*/
Features new_feature(enum part_of_speech pos, Box *new_d1, Box *new_d2, Box *new_d3, char **objects,
		     int ol, Flow *new_flow, Real scale, FeatureMedoid **fms, int mn){
  Track tr1 = allocTrack(1), tr2 = NULL, tr3 = NULL;
  Flow **one_flows = safe_malloc(sizeof *one_flows);
  Features new_feature = NULL;

  tr1->ds[0] = new_d1;
  if (new_d2){
    tr2 = allocTrack(1);
    tr2->ds[0] = new_d2;
  }
  if (new_d3){
    tr3 = allocTrack(1);
    tr3->ds[0] = new_d3;
  }
  one_flows[0] = new_flow;

  new_feature = word_features(pos, tr1, tr2, tr3, 2, 0, NULL, objects, ol, 0,
			      one_flows, 1, scale, fms, mn);
  freeTrack(tr1);
  if (tr2) freeTrack(tr2);
  if (tr3) freeTrack(tr3);
  free(one_flows);
  return new_feature;
}

/* Compute one-track features
   Input:  type                   --> the type of the features to be computed (e.g., velocity, box area)
           tr                     --> a movie of boxes for one participant (i.e., track)
           lookahead              --> number of frames we look forward
	   raw                    --> Raw (1) or quantized (0) features
           medoids                --> for pose (always NULL in the code)
	   objects                --> an array of object names, determined by dataset
	   ol                     --> the length of object names
	   discard_bad_features   --> whether discard the last frame
                                      NOTE: do not discard the last frame if we are
				      computing features for the current frame
	   flows_movie            --> a sequence of optical flows, each one for each frame
	   fl_tt                  --> the length of flows_movie, this number should be the same
	                              with the length of tr
	   scale                  --> for scaling boxes
	   fms                    --> an array of feature medoids, for quantization
	   mn                     --> number of feature medoids
   Output: a sequence of features
           each feature is a vector whose length is decided by the feature type
	   (i.e., ii is decided by what kind of type to be computed)
	   Notice that if we discard bad features, the length of returned features will be
	   smaller than fl_tt
*/

Features one_track_features(enum one_track_feature_type type, Track tr, int lookahead, int raw,
			    void *medoids, char **objects, int ol, int discard_bad_features,
			    Flow **flows_movie, int fl_tt, Real scale, FeatureMedoid **fms, int mn){
  Real magnitude, orientation;
  Features f = NULL;
  Real tmp_x, tmp_y;
  int i, t, tt;
  assert(tr);
  tt = tr->tt;
  assert(medoids == NULL);
  if (raw == 0)
    assert(fms);

  switch(type){
  case POSITION_X:
    /* Position x shouldn't be used directly as feature
       Distance computation is in two-track-features */
    assert(raw == 1);
    f = allocFeatures(tt, 1);
    for (t = 0; t < tt; t ++)
      f->v[t][0] = box_center_x(tr->ds[t]);
    break;
  case POSITION_Y:
    /* Position y shouldn't be used directly as feature
       Distance computation is in two-track-features */
    assert(raw == 1);
    f = allocFeatures(tt, 1);
    for (t = 0; t < tt; t ++)
      f->v[t][0] = box_center_y(tr->ds[t]);
    break;
  case UPPER_Y:
    /* Upper y shouldn't be used directly as feature */
    assert(raw == 1);
    f = allocFeatures(tt, 1);
    for (t = 0; t < tt; t ++)
      f->v[t][0] = tr->ds[t]->y1;
    break;
  case AREA:
    /* This feature should be raw, will be used in box_ratio */
    assert(raw == 1);
    f = allocFeatures(tt, 1);
    for (t = 0; t < tt; t ++)
      f->v[t][0] = box_area(tr->ds[t]);
    break;
  case MODEL_COLOR:
    f = allocFeatures(tt, 1);
    for (t = 0; t < tt; t ++){
      f->v[t][0] = unpack_box_color_h(tr->ds[t]);
      if (raw == 0)
	f->v[t][0] = quantize(f->v[t][0], "color", fms, mn);
    }
    break;
  case MODEL_NAME:
    /* This feature must be provided with objects */
    assert(objects);
    f = allocFeatures(tt, 1);
    for (t = 0; t < tt; t ++){
      for (i = 0; i < ol; i ++)
	if (strcmp(tr->ds[t]->model, objects[i]) == 0)
	  break;
      if (i == ol)
	panic("one_track_features: model %s not in the objects", tr->ds[t]->model);
      f->v[t][0] = i;
    }
    break;
  case FLOW_MAGNITUDE_IN_BOX:
  case FLOW_ORIENTATION_IN_BOX:
    assert(flows_movie);
    /* Notice: the flows_movie should have been padded with
       the last one before being passed in. The actual length relationship
       should be (tt + lookahead - 1) == (tr->tt)
    */
    assert(tt == fl_tt);
    f = allocFeatures(tt, 1);
    for (t = 0; t < tt; t ++){
      tmp_x = average_flow_x_in_box(tr->ds[t], flows_movie[t], scale);
      tmp_y = average_flow_y_in_box(tr->ds[t], flows_movie[t], scale);
      magnitude = DISTANCE_2D(0, 0, tmp_x, tmp_y);
      orientation = atan2(tmp_y, tmp_x);
      if (raw == 0){
	magnitude = quantize(magnitude, "velocity", fms, mn);
	orientation = quantize(orientation, "orientation", fms, mn);
      }
      if (type == FLOW_MAGNITUDE_IN_BOX)
	f->v[t][0] = magnitude;
      else
	f->v[t][0] = orientation;
    }
    break;
  default: panic("one_track_features: type %d unrecognized", type);
  }
  /* Discard bad (padded) features if we wish */
  if (discard_bad_features == 1){
    int new_tt = tt - (lookahead - 1);
    for (t = new_tt; t < tt; t ++)
      free(f->v[t]);
    f->tt = new_tt;
  }
  return f;
}

Features word_features(enum part_of_speech pos, Track tr1, Track tr2, Track tr3, int lookahead, int raw,
		       void *medoids, char **objects, int ol, int discard_bad_features,
		       Flow **flows_movie, int fl_tt, Real scale, FeatureMedoid **fms, int mn){
  /* For shorter code */
#define ONE_TRACK_FEATURES(type, tr) \
  one_track_features(type, tr, lookahead, one_track_raw, medoids, objects, ol, \
		     discard_bad_features, flows_movie, fl_tt, scale, fms, mn)

  Features f = NULL, one_track_f1 = NULL, one_track_f2 = NULL;
  int i, t, tt;
  int one_track_raw = raw;
  Track tr, tr_;
  enum one_track_feature_type velocity_types[] = {FLOW_MAGNITUDE_IN_BOX,
						  FLOW_ORIENTATION_IN_BOX};
  /* At least one track is valid */
  assert(tr1);

  tt = tr1->tt;
  if (tr2) assert(tt == tr2->tt);
  if (tr3) assert(tt == tr3->tt);

  assert(medoids == NULL);

  if (raw == 0) assert(fms);

  /* If discard_bad_features, we expect the feature length returned to be tt-(lookahead-1) */
  tt = (discard_bad_features == 1)? tt-(lookahead-1): tt;
  switch(pos){
  case NOUN:
    /* Feature for a noun is the model name
       Only the first track should be valid */
    assert(tr2 == NULL && tr3 == NULL);
    one_track_raw = raw;
    f = ONE_TRACK_FEATURES(MODEL_NAME, tr1);
    break;
  case VERB:
    /* At least two tracks*/
    assert(tr2);
    if (tr3 == NULL){
      /* Features for a verb with two participants are:
       1. agent velocity magnitude
       2. agent velocity orientation
       3. patient velocity magnitude
       4. patient velocity orientation
       5. agent-patient-x-distance
       6. agent-patient-relative-box-area
      */
      f = allocFeatures(tt, 6);
      // 1 - 4
      for (i = 0; i < 4; i ++){
	if (i < 2) tr = tr1;
	else tr = tr2;
	one_track_raw = raw;
	one_track_f1 = ONE_TRACK_FEATURES(velocity_types[i % 2], tr);
	for (t = 0; t < tt; t ++)
	  f->v[t][i] = one_track_f1->v[t][0];
	freeFeatures(one_track_f1);
      }
      // 5
      one_track_raw = 1;
      one_track_f1 = ONE_TRACK_FEATURES(POSITION_X, tr1);
      one_track_f2 = ONE_TRACK_FEATURES(POSITION_X, tr2);
      for (t = 0; t < tt; t ++){
	f->v[t][4] = DISTANCE_2D(one_track_f1->v[t][0], 0, one_track_f2->v[t][0], 0);
	if (raw == 0)
	  f->v[t][4] = quantize(f->v[t][4], "x_distance", fms, mn);
      }
      freeFeatures(one_track_f1);
      freeFeatures(one_track_f2);
      // 6
      one_track_raw = 1;
      one_track_f1 = ONE_TRACK_FEATURES(AREA, tr1);
      one_track_f2 = ONE_TRACK_FEATURES(AREA, tr2);
      for (t = 0; t < tt; t ++){
	f->v[t][5] = one_track_f1->v[t][0] / one_track_f2->v[t][0];
	if (raw == 0)
	  f->v[t][5] = 1 - quantize(f->v[t][5], "box_ratio", fms, mn);
      }
      freeFeatures(one_track_f1);
      freeFeatures(one_track_f2);
    }
    else{
      /* Features for a verb with three participants are:
       1. agent velocity magnitude
       2. agent velocity orientation
       3. patient velocity magnitude
       4. patient velocity orientation
       5. goal velocity magnitude
       6. goal velocity orientation
       7. agent-patient-x-distance
       8. agent-patient-y-distance
       9. agent-patient-relative-box-area
       10. agent-patient-overlap
       11. agent-goal-x-distance
       12. agent-goal-y-distance
       13. agent-goal-relative-box-area
       14. agent-goal-overlap
       15. patient-goal-x-distance
       16. patient-goal-y-distance
       17. patient-goal-relative-box-area
       18. patient-goal-overlap
      */
      f = allocFeatures(tt, 18);
      // 1 - 6
      for (i = 0; i < 6; i ++){
	if (i < 2) tr = tr1;
	else if (i < 4) tr = tr2;
	else tr = tr3;
	one_track_raw = raw;
	one_track_f1 = ONE_TRACK_FEATURES(velocity_types[i % 2], tr);
	for (t = 0; t < tt; t ++)
	  f->v[t][i] = one_track_f1->v[t][0];
	freeFeatures(one_track_f1);
      }
      // 7 - 18
      for (i = 6; i < 18; i += 4){
	if (i < 10){
	  tr = tr1;
	  tr_ = tr2;
	}
	else if (i < 14){
	  tr = tr1;
	  tr_ = tr3;
	}
	else{
	  tr = tr2;
	  tr_ = tr3;
	}
	/* x-distance */
	one_track_raw = 1;
	one_track_f1 = ONE_TRACK_FEATURES(POSITION_X, tr);
	one_track_f2 = ONE_TRACK_FEATURES(POSITION_X, tr_);
	for (t = 0; t < tt; t ++){
	  f->v[t][i] = DISTANCE_2D(one_track_f1->v[t][0], 0, one_track_f2->v[t][0], 0);
	  if (raw == 0)
	    f->v[t][i] = quantize(f->v[t][i], "x_distance", fms, mn);
	}
	freeFeatures(one_track_f1);
	freeFeatures(one_track_f2);
	/* y-distance */
	one_track_raw = 1;
	one_track_f1 = ONE_TRACK_FEATURES(POSITION_Y, tr);
	one_track_f2 = ONE_TRACK_FEATURES(POSITION_Y, tr_);
	for (t = 0; t < tt; t ++){
	  f->v[t][i+1] = DISTANCE_2D(0, one_track_f1->v[t][0], 0, one_track_f2->v[t][0]);
	  if (raw == 0)
	    f->v[t][i+1] = quantize(f->v[t][i+1], "y_distance", fms, mn);
	}
	freeFeatures(one_track_f1);
	freeFeatures(one_track_f2);
	/* relative-box-area */
	one_track_raw = 1;
	one_track_f1 = ONE_TRACK_FEATURES(AREA, tr);
	one_track_f2 = ONE_TRACK_FEATURES(AREA, tr_);
	for (t = 0; t < tt; t ++){
	  f->v[t][i+2] = one_track_f1->v[t][0] / one_track_f2->v[t][0];
	  if (raw == 0)
	    f->v[t][i+2] = 1 - quantize(f->v[t][i+2], "box_ratio", fms, mn);
	}
	freeFeatures(one_track_f1);
	freeFeatures(one_track_f2);
	/* overlap */
	for (t = 0; t < tt; t ++){
	  f->v[t][i+3] = box_overlap_percentage(tr->ds[t], tr_->ds[t]);
	  if (raw == 0)
	    f->v[t][i+3] = quantize(f->v[t][i+3], "overlap", fms, mn);
	}
      }
    }
    break;
  case ADVERB:
    /* Features for an adverb is:
       agent velocity magnitude
     */
    assert(tr2 == NULL && tr3 == NULL);
    one_track_raw = raw;
    f = ONE_TRACK_FEATURES(FLOW_MAGNITUDE_IN_BOX, tr1);
    break;
  case ADJECTIVE:
    /* Feature for an adjective is the color
       Only the first track should be valid */
    assert(tr2 == NULL && tr3 == NULL);
    one_track_raw = raw;
    f = ONE_TRACK_FEATURES(MODEL_COLOR, tr1);
    break;
  case PREPOSITION:
    /* Feature for a preposition is the relative position of two objects:*/
    assert(tr2 && tr3 == NULL);
    f = allocFeatures(tt, 1);
    one_track_raw = 1;
    one_track_f1 = ONE_TRACK_FEATURES(POSITION_X, tr1);
    one_track_f2 = ONE_TRACK_FEATURES(POSITION_X, tr2);
    for (t = 0; t < tt; t ++){
      if (one_track_f1->v[t][0] < (one_track_f2->v[t][0] - 50))
	f->v[t][0] = 0;
      else if (one_track_f1->v[t][0] > (one_track_f2->v[t][0] + 50))
	f->v[t][0] = 1;
      else
	f->v[t][0] = 2;
    }
    freeFeatures(one_track_f1);
    freeFeatures(one_track_f2);
    break;
  case MOTION_PREPOSITION:
    /* Features for a motion_preposition are:
       1. agent velocity
       2. agent and location distance
    */
    assert(tr2 && tr3 == NULL);
    f = allocFeatures(tt, 2);
    one_track_raw = raw;
    one_track_f1 = ONE_TRACK_FEATURES(FLOW_MAGNITUDE_IN_BOX, tr1);
    for (t = 0; t < tt; t ++)
      f->v[t][0] = one_track_f1->v[t][0];
    freeFeatures(one_track_f1);
    one_track_raw = 1;
    one_track_f1 = ONE_TRACK_FEATURES(POSITION_X, tr1);
    one_track_f2 = ONE_TRACK_FEATURES(POSITION_X, tr2);
    for (t = 0; t < tt; t ++){
      f->v[t][1] = DISTANCE_2D(one_track_f1->v[t][0], 0, one_track_f2->v[t][0], 0);
      if (raw == 0)
	f->v[t][1] = quantize(f->v[t][1], "x_distance", fms, mn);
    }
    freeFeatures(one_track_f1);
    freeFeatures(one_track_f2);
    break;
  default: panic("word_features: part of speech %d unrecognized", pos);
  }
  return f;
}

/* Tam V'Nishlam Shevah L'El Borei Olam */
