/* LaHaShem HaAretz U'Mloah */

#ifndef HMM_H
#define HMM_H

#include "hmm-def.h"
#include "hmm-features.h"

#define HMM_STATES(x) ((x)->uu)

#define DISPLAY_HMM_MAX_U 10

typedef struct {
  RMat logA;
  RVec logB;
  int uu;
} Hmm;

typedef struct {
  int ii;       /* Number of boxes on each frame */
  int tt;       /* Number of frames */
  Hmm *m;       /* The crossed hmm */
  RMat3d bps;   /* box_pair_score[t][b1][b2]: pair score between b1 at t and b2 at t + 1 */
  RMat bss;     /* box_single_score[t][b]: single score of b at t */
} xHmm;

Hmm *allocHMM(int uu);

void constantHMM(Hmm *m, int upper_triangular);

void copyHMM(Hmm *m1, Hmm *m2);

void displayHMM(void *p, Hmm *m);

void freeHMM(Hmm *m);

void normaliseHMM(Hmm *m, int upper_triangular);

int normaliseHMMlinear(Hmm *m, int upper_triangular, enum training_mode train_mode, int* xu);

void randomiseHMM(Hmm *m, int upper_triangular);

void noiseHMM(Hmm *m, int upper_triangular, Real delta);

void smoothHMM(Hmm *m, int upper_triangular, Real eps);

void zeroHMM(Hmm *m);

void zeroHMMlinear(Hmm *m);

void linear2logHMM(Hmm *m);

void log2linearHMM(Hmm *m);

int isZeroHMMlinear(Hmm *m);

int HMM_xStateRange(Hmm **hmms, int ww);

void HMM_decodeXStates(int *us, Hmm **hmms, int u, int ww);

Real HMM_xB(Hmm **hmms, int u, int ww);

Real HMM_xA(Hmm **hmms, int u, int v, int ww);

xHmm *allocXHMM(int uu, int ii, int tt);

void freeXHMM(xHmm *xhmm);

xHmm *initializeXHMM
(Hmm **hmms, int ww, BoxesMovie bm, Flow **flow_movie, int fl_tt, Real scale);

void HMM_calcAlphas(Hmm *m, RVec *logL);

void HMM_calcDeltas(Hmm *m, RVec *logL);

void HMM_calcGammas(Hmm *m, RVec *logL, RVec *gamma);

void HMM_initGlobals(int uu, int tt);

Real HMM_logL(Hmm *m, RVec *logL);

int *HMM_best_state_sequence(Hmm *m, RVec *logL);

Real HMM_updateModel
(Hmm *m, Hmm *new_m, RVec *logL, RVec *gamma, Real log_D, 
 Real postpC, int c, int c_ls, enum training_mode training_mode);

#endif

/* Tam V'Nishlam Shevah L'El Borei Olam */
