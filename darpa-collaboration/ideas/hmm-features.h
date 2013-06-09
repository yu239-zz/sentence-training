/* LaHaShem HaAretz U'Mloah */

#ifndef HMM_FEATURES_H
#define HMM_FEATURES_H

#include "hmm-def.h"

typedef Real (*distance_metric)(Real, Real, Real);

/* A similar struct w.r.t c-optical-flow in scheme */
typedef struct{
  Real *flow;
  int hh;
  int ww;
} Flow;

typedef struct{
  char *name;
  int nn;
  Real *mean;
  Real *sigma;
  int dm_id;
} FeatureMedoid;

struct FeaturesStruct {
  int tt;                     /* The moive length */
  int ii;                     /* The vector length in one frame */
  Real **v;
};

typedef struct FeaturesStruct *Features;

/* Optical flow */
Flow *allocFlow(int ww, int hh);

void set_flow(Flow *fl, Real *flow, int ww, int hh);

void freeFlow(Flow *fl);

Real average_flow_in_box(Box *d, Flow *fl, Real scale, int flag);

Real average_flow_x_in_box(Box *d, Flow *fl, Real scale);

Real average_flow_y_in_box(Box *d, Flow *fl, Real scale);

Box *forward_projected_box_scaled(Box *d, Flow *fl, Real scale);

Real box_pair_score(Box *d1, Box *d2, Flow *fl, Real scale);

Real box_single_score(Box *d);

Real box_similarity(Box *d1, Box *d2);

/* FeatureMedoid */
FeatureMedoid *allocFeatureMedoid(int nn);

void freeFeatureMedoid(FeatureMedoid *fm);

void set_feature_medoid_name(FeatureMedoid *fm, char *name);

void set_feature_medoid_mean(FeatureMedoid *fm, int i, Real x);

void set_feature_medoid_sigma(FeatureMedoid *fm, int i, Real x);

void set_feature_medoid_dm_id(FeatureMedoid *fm, int id);

/* Features */
Features allocFeatures(int tt, int ii);

void freeFeatures(Features f);

int Features_tt(Features f);

int Features_ii(Features f);

Real **Features_v(Features f);

Real get_feature(Features f, int t, int i);

void print_Features(Features f);

/* Quantization functions */
int quantize(Real x, char *type, FeatureMedoid **fms, int mn);

int nearest_1d(Real x, Real *mean, Real *sigma, int nn, distance_metric dm);

Real fake_mahalanobis(Real x, Real mean, Real sigma);

/* from QobiScheme-AD.sc */
Real normalize_rotation(Real rotation);

Real angle_separation(Real x, Real y);

Real my_angle_separation(Real x, Real mean, Real sigma);

/*  Feature computation functions */
Features new_feature
(enum part_of_speech pos, Box *new_d1, Box *new_d2, Box *new_d3, char **objects, 
 int ol, Flow *new_flow, Real scale, FeatureMedoid **fms, int mn);

Features one_track_features
(enum one_track_feature_type type, Track tr, int lookahead, int raw,
 void *medoids, char **objects, int ol, int discard_bad_features,
 Flow **flows_movie, int fl_tt, Real scale, FeatureMedoid **fms, int mn);

Features word_features
(enum part_of_speech pos, Track tr1, Track tr2, Track tr3, int lookahead, int raw,
 void *medoids, char **objects, int ol, int discard_bad_features,
 Flow **flows_movie, int fl_tt, Real scale, FeatureMedoid **fms, int mn);

#endif
/* Tam V'Nishlam Shevah L'El Borei Olam */
