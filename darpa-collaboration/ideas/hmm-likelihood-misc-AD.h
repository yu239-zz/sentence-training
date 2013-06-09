/* This file is just a redeclaration of Real as adouble for the use of ADOLC */

#ifndef HMM_LIKELIHOOD_MISC_AD_H
#define HMM_LIKELIHOOD_MISC_AD_H

#define ADOLC_TAPELESS
#define NUMBER_DIRECTIONS 300

#include <adolc/adouble.h>
#include "hmm-likelihood-AD.h"

struct adRVecStruct {size_t x; adtl::adouble *v;};
struct adRMatStruct {size_t x; size_t y; adtl::adouble **m;};
typedef struct adRVecStruct *adRVec;
typedef struct adRMatStruct *adRMat;

/* Data structures that contain adtl::adouble type*/
typedef struct {
  adRMat logA;
  adRVec logB;
  int uu;
} adHmm;

typedef struct {
  int ii;       /* Number of boxes on each frame */
  int tt;       /* Number of frames */
  adHmm *m;     /* The crossed hmm */
  RMat3d bps;   /* box_pair_score[t][b1][b2]: pair score between b1 at t and b2 at t + 1 */
  RMat bss;     /* box_single_score[t][b]: single score of b at t */
} adXHmm;

typedef struct {int kk; adtl::adouble *p;} adDParam;
typedef adtl::adouble (*adLogLikeF)(Real, adDParam *);
typedef struct {
  adDParam **p;			/* model parameters */
  adLogLikeF *lf;	        /* likelihood functions */
  int ii;			/* number of features */
} adFfm;

typedef struct {
  adHmm *hmm;			
  adFfm **ffm;			/* Output models at each HMM state */
  int uu;			/* number of states in HMM */
  char *name;                   /* HMM name */             
  int n_roles;                  /* Number of roles associated with the HMM for a word in a sentence */
  enum part_of_speech pos;      /* Part-of-speech of the word the HMM associated with */
} adModel;

struct adSentenceStruct {
  int ww;
  adModel **ws;
};

typedef struct adSentenceStruct *adSentence;

/* Functions */
adtl::adouble my_add_exp(adtl::adouble e1, adtl::adouble e2);

adDParam *allocAdDParam(int kk);

void freeAdDParam(adDParam *p);

adRVec allocAdRVec(size_t x);

adRMat allocAdRMat(size_t y, size_t x);

void freeAdRVec(adRVec v);

void freeAdRMat(adRMat m);

adFfm *allocAdFFM(int ii);

void freeAdFFM(adFfm *m);

adModel *allocAdModel(int ii, int uu);

void freeAdModel(adModel *m);

void ad_set_model_name(adModel *m, char *name);

adtl::adouble adLogDiscrete(Real x, adDParam *p);

adtl::adouble adFFM_logL_one(adFfm *m, RVec data);

void ad_setFeatKK(adFfm *m, int i, int kk);

int adHMM_xStateRange(adHmm **hmms, int ww);

void adHMM_decodeXStates(int *us, adHmm **hmms, int u, int ww);

adtl::adouble adHMM_xB(adHmm **hmms, int u, int ww);

adtl::adouble adHMM_xA(adHmm **hmms, int u, int v, int ww);

adHmm *allocAdHMM(int uu);

void freeAdHMM(adHmm *m);

adXHmm *allocAdXHMM(int uu, int ii, int tt);

void freeAdXHMM(adXHmm *xhmm);

adXHmm *initializeAdXHMM
(adHmm **hmms, int ww, BoxesMovie bm, Flow **flow_movie, int fl_tt, Real scale);

void ad_compute_eligible_states
(adSentence *ss, int *nTracks, int nn, IVec **roles_multiple, BoxesMovie bm, 
 enum model_constraint model_constraint,
 /* Output */ IVec *eligible_states, IVec **secondary_eligible_states);

void ad_compute_sentence_priors(adSentence *ss, int nn, int tt, Real *priors);

void ad_compute_crossed_likelihood
(IVec es, int vv, adSentence s, int nTracks, IVec *roles, BoxesMovie bm, Flow **flow_movie, 
 Real scale, char **objects, int ol, FeatureMedoid **fms, int mn, adtl::adouble **outL);

adtl::adouble ad_viterbi_sentence_tracker
(adSentence s, adXHmm *xhmm, int nTracks, IVec *roles, BoxesMovie bm, Flow **flow_movie,
 int fl_tt, Real scale, Real prior, char **objects, int ol, FeatureMedoid **fms,
 int mn, IVec es, IVec *es2, int *box_sequence);

adtl::adouble ad_sentence_likelihood_one
(adSentence s, int nTracks, IVec *roles, BoxesMovie bm, Flow **flow_movie, int fl_tt,
 Real scale, char **objects, int ol, FeatureMedoid **fms, int mn,
 enum model_constraint model_constraint);

adSentence initializeAdSentence(int ww, adModel **ws);

void freeAdSentence(adSentence s);

adModel *unpack_one(Real *paras, int uu, int ii, int *nn, char *model_name,
		    enum part_of_speech model_pos, int model_n_roles, int offset);

adModel **unpack(Real *paras, PackInfo *stats, char **model_names,
		 enum part_of_speech *model_pos, int *model_n_roles, int *selected, int *block_size);

#endif
