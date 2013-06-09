/* LaHaShem HaAretz U'Mloah */

#ifndef HMM_CONTROL_H
#define HMM_CONTROL_H

#include "hmm-def.h"
#include "hmm-data.h"

typedef struct {
  Hmm *hmm;			
  Ffm **ffm;			
  int uu;			/* number of states in HMM */
  char *name;                   /* HMM name */             
  int n_roles;                  /* Number of roles associated with the HMM for a word in a sentence */
  enum part_of_speech pos;      /* Part-of-speech of the word the HMM associated with */
} Model;

/* A sentence is just a list of HMMs */
struct SentenceStruct {
  int ww;
  Model **ws;
};

typedef struct SentenceStruct *Sentence;

void setGlobalWeights(int a, int b);

void initGlobals(int ll, int uu, RMat *data);

Model *allocModel(int ii, int uu);

void copyModel(Model *dst_m, Model *src_m);

void uniformModel(Model *m, int ut);

void randomiseModel(Model *m, int ut);

void noiseModel(Model *m, int upper_triangular, Real delta);

void smoothModel(Model *m, int upper_triangular, Real eps);

int normalizeModel(Model *m, int upper_triangular, enum training_mode train_mode);

void defineContFeat(Model *m, int i, Real sigma);

void defineRadialFeat(Model *m, int i);

void defineDiscreteFeat(Model *m, int i);

void displayModel(void *p, Model *m);

void print_model(Model *m);

void freeModel(Model *m);

void zeroModel(Model *m);

void linear2logModel(Model *m);

void log2linearModel(Model *m);

int isZeroModel(Model *m);

Sentence initializeSentence(int ww, Model **ws);

void freeSentence(Sentence s);

Real logLike(Model *m, RMat data);

Real logLike_with_box_scores(Model *m, RMat data, RMat score_mat);

int *best_state_sequence(Model *m, RMat data);

void force_init_globals(void);

void compute_posterior
(Model **m, RMat *data, Real *prior, int *c_ls, int ll, int cc, 
 enum training_mode training_mode, 
 /* outputs */ Real *O, Real *like, Real **postpC);

int update
(Model **m, Hmm **tmp_hmm, RMat *data, Real **postpC, Real log_D,
 int *c_ls, int ll, int cc, enum training_mode training_mode, int upper_triangular);

/* -- Sentence training functions -- */

void decode_box_states(int j, int ii, int nTracks, int *js);

void compute_eligible_states
(Sentence *ss, int *nTracks, int nn, IVec **roles_multiple, 
 BoxesMovie bm, enum model_constraint model_constraint,
 /* Output */
 IVec *eligible_states, IVec **secondary_eligible_states);

void compute_crossed_likelihood
(IVec es, int vv, Sentence s, int nTracks, IVec *roles, BoxesMovie bm, 
 Flow **flow_movie, Real scale, char **objects, int ol, FeatureMedoid **fms, 
 int mn, Real **outL);

Real viterbi_tracker_score
(Real ***bps, Real **bss, int nTracks, int ii, int tt, IVec es, IVec *es2);

void sentence_calc_alpha
(IVec es, IVec *es2, int vv, int ii, int tt, int nTracks, Real **a, Real *b, 
 Real ***bps, Real **bss, Real **outL, Real **alpha);

void sentence_calc_beta
(IVec es, IVec *es2, int vv, int ii, int tt, int nTracks, Real **a, 
 Real ***bps, Real **bss, Real **outL, Real **beta);

void sentence_calc_gamma
(int uu, int tt, Real **alpha, Real **beta, Real like, Real **gamma);

void compute_sentence_priors(Sentence *ss, int nn, int tt, Real *priors);

void compute_sentence_statistics
(Sentence *ss, xHmm **xhmms, int *nTracks, int nn, IVec **roles_multiple, 
 BoxesMovie bm, Flow **flow_movie, int fl_tt, Real scale, Real *priors, 
 char **objects, int ol, FeatureMedoid **fms, int mn, IVec *es, IVec **es2,
 /* Ouput */
 Real ***xGamma, Real ***xAlpha, Real ***xBeta, Real ***xState_like, 
 Real *like, Real *posteriors, Real *total_post);

Real *sentence_likelihoods_one_video
(Sentence *ss, int nn, int *nTracks, IVec **roles, BoxesMovie bm, 
 Flow **flow_movie, int fl_tt, Real scale, char **objects, int ol, 
 FeatureMedoid **fms, int mn, enum model_constraint model_constraint, 
 int final_state);

Real sentence_maximum_one
(Sentence s, int nTracks, IVec *roles, BoxesMovie bm, Flow **flow_movie, int fl_tt,
 Real scale, char **objects, int ol, FeatureMedoid **fms, int mn, 
 enum model_constraint model_constraint, int final_state, int *box_sequence);

Real viterbi_sentence_tracker
(Sentence s, xHmm *xhmm, int nTracks, IVec *roles, BoxesMovie bm, Flow **flow_movie,
 int fl_tt, Real scale, Real prior, char **objects, int ol, FeatureMedoid **fms,
 int mn, IVec es, IVec *es2, int sum_or_max, int final_state, int *box_sequence);

UpdateResults *updateX
(Sentence *competition_set, Sentence *scratch_models,
 int *nTracks, int nSentences, IVec **roles_multiple, BoxesMovie bm,
 Flow **flow_movie, int fl_tt, Real scale, Real log_D,
 enum training_mode training_mode, char **objects, int ol, 
 FeatureMedoid **fms, int mn, enum model_constraint model_constraint);

void HMM_updateXModel
(Sentence s, Sentence scratch, xHmm *xhmm, IVec es, IVec *es2, int nTracks, 
 Real **alpha, Real **beta, Real **state_like, Real *gamma0, Real like, 
 Real posterior, Real log_D, enum training_mode training_mode, int positive);

void FFM_updateXModel
(Sentence s, Sentence scratch, IVec *roles, Real **gamma, int vv, IVec es, 
 int nTracks, BoxesMovie bm, Flow **flow_movie, Real scale, Real posterior, 
 Real log_D, enum training_mode training_mode, int positive, char **objects, 
 int ol, FeatureMedoid **fms, int mn);

void removeStates(Model *m, int *xu, Hmm* hmm);

int model_ii(Model *m);

int model_nn(Model *m, int i);

int model_feature_type(Model *m, int i);

Real model_parameter(Model *m, int u, int i, int n);

int model_uu(Model *m);

Hmm *model_hmm(Model *m);

Ffm *model_ffm(Model *m, int i);

Real model_a(Model *m, int u, int v);

Real model_b(Model *m, int u);

char *model_name(Model *m);

int model_n_roles(Model *m);

enum part_of_speech model_pos(Model *m);

void set_model_parameter(Model *m, int u, int i, int n, Real x);

void set_model_a(Model *m, int u, int v, Real x);

void set_model_a_linear(Model *m, int u, int v, Real x);

void set_model_b(Model *m, int u, Real x);

void set_model_b_linear(Model *m, int u, Real x);

void set_model_name(Model *m, char * name);

void set_model_n_roles(Model *m, int n);

void set_model_pos(Model *m, enum part_of_speech pos);

char *model_pos_str(enum part_of_speech pos);

#endif

/* Tam V'Nishlam Shevah L'El Borei Olam */
