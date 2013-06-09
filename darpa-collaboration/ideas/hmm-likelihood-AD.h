#ifndef HMM_LIKELIHOOD_AD_H
#define HMM_LIKELIHOOD_AD_H

typedef struct {
  int n_models;
  int *uu;
  int *ii;
  int **nn;
} PackInfo;

#ifdef __cplusplus   // This file is included by both C and C++
extern "C" {
#endif

PackInfo *allocPackInfo(int n);

void freePackInfo(PackInfo *stats);

void set_packinfo_uu(PackInfo *stats, int n, int uu);

void set_packinfo_ii(PackInfo *stats, int n, int ii);

void set_packinfo_nn(PackInfo *stats, int n, int i, int nn);

Real *sentence_derivatives_one_video
(Real *paras, int nSentences, int n_paras, int *sentence_indices, int *ww, int *nTracks, 
 IVec *roles, BoxesMovie bm, Flow **flow_movie, int fl_tt, Real scale, PackInfo *stats, 
 char **model_names, enum part_of_speech *model_pos, int *model_n_roles, char **objects, 
 int ol, FeatureMedoid **fms, int mn, enum model_constraint model_constraint);

#ifdef __cplusplus
}
#endif

void sentence_derivatives
(Real *paras, int n_paras, int *sentence_indices, int ww, int nTracks, IVec *roles, 
 BoxesMovie bm, Flow **flow_movie, int fl_tt, Real scale, PackInfo *stats, 
 char **model_names, enum part_of_speech *model_pos, int *model_n_roles, char **objects, 
 int ol, FeatureMedoid **fms, int mn, enum model_constraint model_constraint,
 /* Output */ Real *gradients);

#endif
