/* LaHaShem HaAretz U'Mloah */

/* Program to train and evaluate hidden Markov models */

#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <string.h>
#include <math.h>
#include "hmm.h"
#include "hmm-rand.h"
#include "hmm-data.h"
#include "hmm-control.h"

static RMat g_alpha = NULL;
static RMat g_delta = NULL;
static RMat g_deltav = NULL;
static RMat g_beta = NULL;
static RMat3d g_psi = NULL;

/* allocHMM - Allocates space for an HMM and returns a pointer to it. */
Hmm *allocHMM(int uu)
{ Hmm *m = safe_malloc(sizeof *m);
  assert(uu>0);
  m->logA = allocRMat(uu, uu);
  m->logB = allocRVec(uu);
  m->uu = uu;
  return m;}

/* constantHMM -
 *  Initialises an HMM with constant transition log probabilities.
 */
void constantHMM(Hmm *m, int upper_triangular)
{ int u, v;
  zeroHMM(m);
  for (u = 0; u<m->uu; u++)
  { VECTOR(m->logB)[u] = 0;
    for (v = upper_triangular?u:0; v<m->uu; v++) MATRIX(m->logA)[u][v] = 0;}
  normaliseHMM(m, upper_triangular);}

void copyHMM(Hmm *m1, Hmm *m2)
{ int u;

  if(m1->uu != m2->uu){
    freeHMM(m1);
    m1 = allocHMM(m2->uu);
  }

  copyRVec(VECTOR(m1->logB), VECTOR(m2->logB), m1->uu);
  for (u = 0; u<m1->uu; u++)
  { copyRVec(MATRIX(m1->logA)[u], MATRIX(m2->logA)[u], m1->uu);}
}

/* displayHMM - Writes the HMM `m' in a human-readable format to output
 *  stream `f'.  Only displays a maximum of DISPLAY_HMM_MAX_U states
 *  on a single line.
 */
void displayHMM(void *p, Hmm *m)
{ int i, u;
  FILE *f = (FILE *)p;
  /* display B vector */
  fprintf(f, "%8s", "");
  for (u = 0; u<DISPLAY_HMM_MAX_U&&u<m->uu; u++) fprintf(f, " %4d", u);
  fprintf(f, "\n");
  fprintf(f, "HMM:  B:");
  for (u = 0; u<DISPLAY_HMM_MAX_U&&u<m->uu; u++)
  { fprintf(f, " %4.2f", my_exp(VECTOR(m->logB)[u]));}
  fprintf(f, "\n");
  for (;u<m->uu; u++)
  { fprintf(f, "\n");
    fprintf(f, "%8s", "");
    for (i = 0; i<DISPLAY_HMM_MAX_U&&i+u<m->uu; u++, i++) {
      fprintf(f, " %4d", i+u);
    }
    fprintf(f, "\n");
    for (i = 0; i<DISPLAY_HMM_MAX_U&&u<m->uu; u++, i++)
    { fprintf(f, " %4.2f", my_exp(VECTOR(m->logB)[u]));}
    fprintf(f, "\n");}
  /* display A matrix */
  fprintf(f, "%8s", "A:");
  for (u = 0; u<DISPLAY_HMM_MAX_U&&u<m->uu; u++) fprintf(f, " %4d", u);
  fprintf(f, "\n");
  for (u = 0; u<DISPLAY_HMM_MAX_U&&u<m->uu; u++)
  { fprintf(f, "%8d", u);
    for (i = 0; i<DISPLAY_HMM_MAX_U&&i<m->uu; i++)
    { fprintf(f, " %4.2f", my_exp(MATRIX(m->logA)[u][i]));}
    fprintf(f, "\n");}}

/* freeHMM - Frees a previously allocated HMM. */
void freeHMM(Hmm *m)
{ freeRMat(m->logA);
  freeRVec(m->logB);
  free(m);}

/* normaliseHMM -
 *  Normalises an HMM so that the transition log probabilities from each
 *  state sum to zero.
 */
void normaliseHMM(Hmm *m, int upper_triangular)
{ int u, v;
  Real sum;
  /* this deals with unreachable states */
  Real delta_correction = my_log(CORRECTION_EPS);
  sum = sumOfLogs(VECTOR(m->logB), m->uu);
  assert(sum>NEGATIVE_INFINITY);
  for (u = 0; u<m->uu; u++) VECTOR(m->logB)[u] -= sum;
  for (u = 0; u<m->uu; u++)
  { sum = sumOfLogs(MATRIX(m->logA)[u], m->uu);
    if (sum == NEGATIVE_INFINITY)
      { for (v = upper_triangular?u:0; v<m->uu; v++)
	{ MATRIX(m->logA)[u][v] = delta_correction; }
	sum = sumOfLogs(MATRIX(m->logA)[u], m->uu);}
    for (v = 0; v<m->uu; v++) { MATRIX(m->logA)[u][v] -= sum;}}}

/* normaliseHMMlinear -
    Normalises an HMM in the original space so that transition probabilites
    sum to 1. After this, convert all the values from the original space to
    log space.
 */
int normaliseHMMlinear(Hmm *m, int upper_triangular, enum training_mode train_mode, int *xu)
{ int u, v, w;
  Real sum, corrected_sum;
  int flag;
  sum = 0.0;
  for (u = 0; u < m->uu; u++)
    sum += VECTOR(m->logB)[u];

  assert(fabs(sum)>0.0);
  flag = 0;
  corrected_sum = NEGATIVE_INFINITY;
  for (u = 0; u<m->uu; u++) {
    if(VECTOR(m->logB)[u]/sum < -1e-50){
      printf("normaliseHMMlinear: b error\n");
      return FALSE;
    }
    VECTOR(m->logB)[u] = my_log(VECTOR(m->logB)[u]/sum);
    /* Clip the value to zero if it is smaller than CORRECTION_EPS */
    if(train_mode == HMM_DT
       && VECTOR(m->logB)[u] != NEGATIVE_INFINITY
       && VECTOR(m->logB)[u] < my_log(CORRECTION_EPS)){
      printf("normaliseHMMlinear: clip zero\n");
      VECTOR(m->logB)[u] = NEGATIVE_INFINITY;
      flag = 1;
    }
    corrected_sum = add_exp(corrected_sum, VECTOR(m->logB)[u]);
  }
  assert(corrected_sum != NEGATIVE_INFINITY);
  if(flag){
    for(u = 0; u < m->uu; u ++)
      VECTOR(m->logB)[u] -= corrected_sum;
  }
  for (u = 0; u<m->uu; u++)
  { sum = 0.0;
    w = (upper_triangular?u:0);
    for (v = w; v<m->uu; v++) sum += MATRIX(m->logA)[u][v];
    if (sum==0.0) {
      /* A state never jumps to another state (include itself)
	 This is not the unreachable state. It may be a final state or a useless state.
	 An unreachable state is finally to be a useless state.
         We can just keep all the outward transition probabilities zero. */
      // Do NOTHING. We don't want a corrected uniform distribution.
      for(v = 0; v < m->uu; v ++)
	MATRIX(m->logA)[u][v] = NEGATIVE_INFINITY;
      continue;
    }
    flag = 0;
    corrected_sum = NEGATIVE_INFINITY;
    for (v = 0; v<m->uu; v ++) {
      if(MATRIX(m->logA)[u][v]/sum < -1e-50){
	printf("normaliseHMMlinear: a error\n");
	return FALSE;
      }
      MATRIX(m->logA)[u][v] = my_log(MATRIX(m->logA)[u][v]/sum);
      /* Clip the value to zero if it is smaller than CORRECTION_EPS */
      if(train_mode == HMM_DT
	 && MATRIX(m->logA)[u][v] != NEGATIVE_INFINITY
      	 && MATRIX(m->logA)[u][v] < my_log(CORRECTION_EPS)){
	printf("normaliseHMMlinear: clip zero\n");
      	MATRIX(m->logA)[u][v] = NEGATIVE_INFINITY;
      	flag = 1;
      }
      corrected_sum = add_exp(corrected_sum, MATRIX(m->logA)[u][v]);
    }
    assert(corrected_sum != NEGATIVE_INFINITY);
    if(flag){
      for(v = 0; v < m->uu; v ++)
    	MATRIX(m->logA)[u][v] -= corrected_sum;
    }
  }
  /* Check whether any column in logA has entries that are all NEGATIVE_INFINITY,
     which is an unreachable state. */
  if (xu != NULL){
    memset(xu, 0, sizeof(int) * m->uu);
    for(u = 0; u < m->uu; u ++){
      flag = 0;
      /* 1. The entry of initial probability is 0 */
      if(VECTOR(m->logB)[u] == NEGATIVE_INFINITY) flag ++;
      /* 2. No other state (include itself) can reach this state */
      for(v = 0; v < m->uu; v ++)
	if(MATRIX(m->logA)[v][u] != NEGATIVE_INFINITY) break;
      if(v == m->uu) flag ++;
      if(flag == 2){
	printf("AN UNREACHABLE STATE IS REMOVED! State_#: %d Total state_#: %d\n", u, m->uu);
	xu[u] = 1;
      }
    }
  }
  return TRUE;
}

/* randomiseHMM -
 *  Initialises an HMM with normalised random transition log probabilities.
 */
void randomiseHMM(Hmm *m, int upper_triangular)
{ int u, v;
  zeroHMM(m);
  for (u = 0; u<m->uu; u++)
  { VECTOR(m->logB)[u] = my_log(RANDOM);
    for (v = upper_triangular?u:0; v<m->uu; v++)
    { MATRIX(m->logA)[u][v] = my_log(RANDOM);}}
  normaliseHMM(m, upper_triangular);}

/* Add noise to m */
void noiseHMM(Hmm *m, int upper_triangular, Real delta){
  int u, v;
  for (u = 0; u < m->uu; u ++){
    VECTOR(m->logB)[u] = add_exp(VECTOR(m->logB)[u], my_log(delta * RANDOM));
    for (v = (upper_triangular? u: 0); v < m->uu; v ++){
      MATRIX(m->logA)[u][v] = add_exp(MATRIX(m->logA)[u][v], my_log(delta * RANDOM));
    }
  }
  normaliseHMM(m, upper_triangular);
}

void smoothHMM(Hmm *m, int upper_triangular, Real eps){
  int u, v;
  for (u = 0; u < m->uu; u ++){
    if (VECTOR(m->logB)[u] == NEGATIVE_INFINITY)
      VECTOR(m->logB)[u] = my_log(eps);
    for (v = (upper_triangular? u: 0); v < m->uu; v ++)
      if (MATRIX(m->logA)[u][v] == NEGATIVE_INFINITY)
	MATRIX(m->logA)[u][v] = my_log(eps);
  }
  normaliseHMM(m, upper_triangular);
}

/* zeroHMM - Sets all of the transition probabilities to 0.0. */
void zeroHMM(Hmm *m)
{ int u, v;
  for (u = 0; u<m->uu; u++)
  { VECTOR(m->logB)[u] = NEGATIVE_INFINITY;
    for (v = 0; v<m->uu; v++)
    { MATRIX(m->logA)[u][v] = NEGATIVE_INFINITY;}}}

/* zeroHMMlinear - Sets all of the transition probabilities to 0.0 in the
   orginal space. This function is needed in dt training because during update a
   negative value may occur. */
void zeroHMMlinear(Hmm *m)
{ int u, v;
  for (u = 0; u<m->uu; u++)
  { VECTOR(m->logB)[u] = 0.0;
    for (v = 0; v<m->uu; v++)
    { MATRIX(m->logA)[u][v] = 0.0;}}}

void linear2logHMM(Hmm *m){
  int u, v;
  for (u = 0; u < m->uu; u ++){
    VECTOR(m->logB)[u] = my_log(VECTOR(m->logB)[u]);
    for (v = 0; v < m->uu; v ++)
      MATRIX(m->logA)[u][v] = my_log(MATRIX(m->logA)[u][v]);
  }
}

void log2linearHMM(Hmm *m){
  int u, v;
  for (u = 0; u < m->uu; u ++){
    VECTOR(m->logB)[u] = my_exp(VECTOR(m->logB)[u]);
    for (v = 0; v < m->uu; v ++)
      MATRIX(m->logA)[u][v] = my_exp(MATRIX(m->logA)[u][v]);
  }
}

int isZeroHMMlinear(Hmm *m){
  int u, v, zero = 1;
  for (u = 0; u < m->uu; u ++){
    if (VECTOR(m->logB)[u] != 0){
      zero = 0;
      break;
    }
    for (v = 0; v < m->uu; v ++){
      if (MATRIX(m->logA)[u][v] != 0){
	zero = 0;
	break;
      }
    }
    if (zero == 0) break;
  }
  return zero;
}

xHmm *allocXHMM(int uu, int ii, int tt){
  assert(uu > 0 && ii > 0 && tt > 0);
  xHmm *xhmm = safe_malloc(sizeof *xhmm);
  xhmm->m = allocHMM(uu);
  xhmm->ii = ii;
  xhmm->tt = tt;
  if (tt > 1)
    xhmm->bps = allocRMat3d(tt - 1, ii, ii);
  else xhmm->bps = NULL;
  xhmm->bss = allocRMat(tt, ii);
  return xhmm;
}

void freeXHMM(xHmm *xhmm){
  if (xhmm != NULL){
    freeHMM(xhmm->m);
    freeRMat(xhmm->bss);
    if (xhmm->bps) freeRMat3d(xhmm->bps);
    free(xhmm);
  }
}

/* HMM_xStateRange -
   Calculate the total number of states after crossing all the hmms' states
 */
int HMM_xStateRange(Hmm **hmms, int ww)
{ int w, tot = 1;
  for(w = 0; w < ww; w ++)
    tot *= hmms[w]->uu;
  return tot;
}

/* HMM_decodeXStates -
   Given an xState, decode it into several states, each state for each hmm
 */

void HMM_decodeXStates(int *us, Hmm **hmms, int u, int ww)
{ int w;
  for(w = ww - 1; w >= 0; w --){
    us[w] = u % hmms[w]->uu;
    u /= hmms[w]->uu;
  }
}

/* HMM_xB -
   Calculate the crossed 'b' value (initial probability)
 */
Real HMM_xB(Hmm **hmms, int u, int ww)
{
  int *us = safe_malloc(ww * sizeof *us);
  int w;
  Real tot = 0;
  HMM_decodeXStates(us, hmms, u, ww);
  for(w = 0; w < ww; w ++)
    tot += VECTOR(hmms[w]->logB)[us[w]];
  free(us);
  return tot;
}

/* HMM_xA -
   Calculate the crossed 'a' value (transition probability)
*/

Real HMM_xA(Hmm **hmms, int u, int v, int ww)
{
  int *us = safe_malloc(ww * sizeof *us);
  int *vs = safe_malloc(ww * sizeof *vs);
  int w;
  Real tot = 0;
  HMM_decodeXStates(us, hmms, u, ww);
  HMM_decodeXStates(vs, hmms, v, ww);
  for(w = 0; w < ww; w ++)
    tot += MATRIX(hmms[w]->logA)[us[w]][vs[w]];
  free(us);
  free(vs);
  return tot;
}

/* Allocate and initialize a crossed HMM for a sentence with ww hmms
   Also precompute the box pair and single costs for the boxes movie
*/
xHmm *initializeXHMM(Hmm **hmms, int ww, BoxesMovie bm,
		     Flow **flow_movie, int fl_tt, Real scale){
  int t, tt, i, j, ii, uu, u, v;
  Real ***bps, **bss, **a, *b;
  Box ***ds;
  xHmm *xhmm;
  assert(bm->tt == fl_tt);
  tt = bm->tt;
  ii = bm->ii;
  uu = HMM_xStateRange(hmms, ww);
  xhmm = allocXHMM(uu, ii, tt);
  /* Compute the box pair cost and single cost */
  bps = MATRIX(xhmm->bps);
  bss = MATRIX(xhmm->bss);
  ds = bm->ds;
  for (t = 0; t < tt; t ++)
    for (i = 0; i < ii; i ++){
      bss[t][i] = box_single_score(ds[t][i]);
      if (t < tt - 1){
      	for (j = 0; j < ii; j ++){
      	  bps[t][i][j] = box_similarity(ds[t][i], ds[t+1][j]);
      	  bps[t][i][j] += box_pair_score(ds[t][i], ds[t+1][j], flow_movie[t], scale);
      	}
      }
    }
  /* Compute the crossed HMM initial and transition probability */
  a = MATRIX(xhmm->m->logA);
  b = VECTOR(xhmm->m->logB);
  for (u = 0; u < uu; u ++){
    b[u] = HMM_xB(hmms, u, ww);
    for (v = 0; v < uu; v ++){
      a[u][v] = HMM_xA(hmms, u, v, ww);
    }
  }
  return xhmm;
}

/* -- processing functions -- */

/* HMM_calcAlphas -
 *  Given an HMM and a vector of log likelihoods for states assigned to
 *  data vectors in the sequence, calculates the alpha values from the
 *  forward-backward algorithm.
 */
void HMM_calcAlphas(Hmm *m, RVec *logL)
{ int t, u, v, tt = VLENGTH(logL[0]);
  Real **al, **a = MATRIX(m->logA), *b = VECTOR(m->logB);
  HMM_initGlobals(m->uu, tt);
  al = MATRIX(g_alpha);
  /* alpha[u][1] = b[u]*logL[u][1]
   * alpha[u][t] = sum(v = 1 ... m->uu, a[v][u]*alpha[v][t-1])*logL[u][t]
   */
  for (u = 0; u<m->uu; u++)
    al[u][0] = VECTOR(logL[u])[0]+b[u];
  for (t = 1; t<tt; t++)
  { for (u = 0; u<m->uu; u++)
    { al[u][t] = NEGATIVE_INFINITY;
      for (v = 0; v<m->uu; v++)
      { al[u][t] = add_exp(al[u][t], a[v][u]+al[v][t-1]);}
      al[u][t] += VECTOR(logL[u])[t];
    }
  }
}

/* HMM_calcDeltas -
 *  Given an HMM and a vector of log likelihoods for states assigned to
 *  data vectors in the sequence, calculates the delta values from the
 *  forward-backward algorithm.
 */
void HMM_calcDeltas(Hmm *m, RVec *logL)
{ int t, u, v, tt = VLENGTH(logL[0]);
  /* dev could be int. */
  Real **de, **dev, **a = MATRIX(m->logA), *b = VECTOR(m->logB), d;
  HMM_initGlobals(m->uu, tt);
  de = MATRIX(g_delta);
  dev = MATRIX(g_deltav);	
  /* delta[u][1] = b[u]*logL[u][1]
   * delta[u][t] = max(v = 1 ... m->uu, a[v][u]*delta[v][t-1])*logL[u][t]
   */
  for (u = 0; u<m->uu; u++) de[u][0] = VECTOR(logL[u])[0]+b[u];
  for (t = 1; t<tt; t++)
  { for (u = 0; u<m->uu; u++)
    { de[u][t] = NEGATIVE_INFINITY;
      dev[u][t] = 0;		
      for (v = 0; v<m->uu; v++)
      { d = a[v][u]+de[v][t-1];
	if (d>de[u][t])
	{ de[u][t] = d;
	  dev[u][t] = v;}}	
      de[u][t] += VECTOR(logL[u])[t];}}}

/* HMM_initGlobals -
 *  Given a number of states and a sequence length, allocates or reallocates
 *  memory for internal variables.
 */
void HMM_initGlobals(int uu, int tt)
{ if (g_alpha==NULL||COLUMNS(g_alpha)<tt||ROWS(g_alpha)<uu)
  { if (g_alpha!=NULL)
    { freeRMat(g_alpha);
      freeRMat(g_delta);
      freeRMat(g_deltav);	
      freeRMat(g_beta);
      freeRMat3d(g_psi);}
    g_alpha = allocRMat(uu, tt);
    g_delta = allocRMat(uu, tt);
    g_deltav = allocRMat(uu, tt); 
    g_beta = allocRMat(uu, tt);
    g_psi = allocRMat3d(uu, uu, tt);}}

/* HMM_logL -
 *  Given an HMM and a vector of log likelihoods for states assigned to
 *  data vectors in the sequence, returns the log likelihood of the data
 *  given the HMM.
 */
Real HMM_logL(Hmm *m, RVec *logL)
{ int u, tt = VLENGTH(logL[0]);
  Real l = NEGATIVE_INFINITY;
  /* P(sequence|m) = sum(u = 1 ... m->uu, alpha[u][tt]) */
  HMM_calcAlphas(m, logL);
  for (u = 0; u<m->uu; u++) l = add_exp(l, MATRIX(g_alpha)[u][tt-1]);
  return l;}

/* HMM_best_state_sequence -
 *  Given an HMM and a vector of log likelihoods for states assigned to
 *  data vectors in the sequence, returns the best state sequence of the data
 *  given the HMM.
 */
int *HMM_best_state_sequence(Hmm *m, RVec *logL)
{ int u, t, tt = VLENGTH(logL[0]), *rv;
  Real b, d;
  rv = safe_malloc(tt * sizeof *rv);
  HMM_calcDeltas(m, logL);
  b = NEGATIVE_INFINITY;
  rv[tt-1] = 0;
  for (u = 0; u<m->uu; u++)
  { d = MATRIX(g_delta)[u][tt-1];
    if (d>b)
    { b = d;
      rv[tt-1] = u;}}
  for (t = tt-2; t>=0; t--) rv[t] = MATRIX(g_deltav)[rv[t+1]][t+1];
  return rv;}

/* HMM_calcGammas computes gammas, which are the probabilities that
 * each frame is in each state
 */
void HMM_calcGammas(Hmm *m, RVec *logL, RVec *gamma) {
  int t, u, v, tt = VLENGTH(logL[0]);
  Real **a = MATRIX(m->logA), **al, **be;
  Real like;

  assert(VLENGTH(logL[0])==VLENGTH(gamma[0]));
  HMM_initGlobals(m->uu, tt);
  al = MATRIX(g_alpha);
  be = MATRIX(g_beta);
  /* calculate alpha's */
  HMM_calcAlphas(m, logL);
  /* calculate beta's -
   * beta[u][tt] = 1
   * beta[u][t] = sum(v = 1 ... m->uu, a[u][v]*beta[v][t+1]*logL[v][t+1])
   */
  for (u = 0; u<m->uu; u++) be[u][tt-1] = 0.0;
  for (t = tt-2; t>=0; t--)
    { for (u = 0; u<m->uu; u++)
	{ be[u][t] = NEGATIVE_INFINITY;
	  for (v = 0; v<m->uu; v++)
	    { be[u][t] =
		add_exp(be[u][t], a[u][v]+be[v][t+1]+VECTOR(logL[v])[t+1]);}}}
  /* calculate logL of sequence -
   * P(sequence|m) = sum(u = 1 ... m->uu, alpha[u][tt])
   */
  like = NEGATIVE_INFINITY;
  for (u = 0; u<m->uu; u++) like = add_exp(like, al[u][tt-1]);
  /* calculate responsibilities
   *               alpha[u][t]*beta[u][t]
   * gamma[u][t] = ----------------------
   *                    P(data|model)
   */
  for (t = 0; t<tt; t++)
    { for (u = 0; u<m->uu; u++) VECTOR(gamma[u])[t] = al[u][t]+be[u][t]-like;}}

/* HMM_updateModel -
 *  Given an HMM and a vector of log likelihoods for states in the sequences,
 *  calculates the responsibilities of each state in the HMM for each symbol
 *  in the sequences, and maximises the model parameters based on the
 *  assigned log likelihoods.
*/
Real HMM_updateModel(Hmm *m, Hmm *new_m, RVec *logL, RVec *gamma, Real log_D,
		     Real postpC, int c, int c_ls,
		     enum training_mode training_mode) {
  int t, u, v, tt = VLENGTH(logL[0]);
  Real **a = MATRIX(m->logA), *b = VECTOR(m->logB), **al, **be, ***ps;
  Real logD = 0, like, dtf;
  int Sc = (c==c_ls);
  switch (training_mode) {
  case HMM_ML:
    assert(postpC==0.0);
    logD = NEGATIVE_INFINITY;
    break;
  case HMM_DT:
    assert(c>=0&&c_ls>=0);
    logD = log_D;
    break;
  default: panic("unrecognized training mode");
  }
  assert(VLENGTH(logL[0])==VLENGTH(gamma[0]));
  HMM_initGlobals(m->uu, tt);
  al = MATRIX(g_alpha);
  be = MATRIX(g_beta);
  ps = MATRIX(g_psi);
  /* calculate alpha's */
  HMM_calcAlphas(m, logL);
  /* calculate beta's -
   * beta[u][tt] = 1
   * beta[u][t] = sum(v = 1 ... m->uu, a[u][v]*beta[v][t+1]*logL[v][t+1])
   */
  for (u = 0; u<m->uu; u++) be[u][tt-1] = 0.0;
  for (t = tt-2; t>=0; t--)
  { for (u = 0; u<m->uu; u++)
    { be[u][t] = NEGATIVE_INFINITY;
      for (v = 0; v<m->uu; v++)
      { be[u][t] =
	add_exp(be[u][t], a[u][v]+be[v][t+1]+VECTOR(logL[v])[t+1]);}}}

  /* calculate logL of sequence -
   * P(sequence|m) = sum(u = 1 ... m->uu, alpha[u][tt])
   */
  like = NEGATIVE_INFINITY;
  for (u = 0; u<m->uu; u++)
    like = add_exp(like, al[u][tt-1]);

  /* A sample that can NEVER belong to this category */
  if(like == NEGATIVE_INFINITY){
    assert(postpC == 0.0);
    assert(Sc==0);
  }

  /* calculate responsibilities
   *               alpha[u][t]*beta[u][t]
   * gamma[u][t] = ----------------------
   *                    P(data|model)
   */
  for (t = 0; t<tt; t++){
     for (u = 0; u<m->uu; u++){
       if(like!=NEGATIVE_INFINITY)
	 VECTOR(gamma[u])[t] = al[u][t]+be[u][t]-like;
       else
	 VECTOR(gamma[u])[t] = NEGATIVE_INFINITY;
     }
  }
  /* calculate time-indexed transition probabilities
   *                alpha[u][t]*a[u][v]*logL[v][t+1]*beta[v][t+1]
   * psi[u][v][t] = ---------------------------------------------
   *                               P(data|model)
   */
  for (u = 0; u<m->uu; u++){
    for (v = 0; v<m->uu; v++){
      for (t = 0; t<tt-1; t++){
	if(like!=NEGATIVE_INFINITY)
	  ps[u][v][t] = al[u][t]+a[u][v]+VECTOR(logL[v])[t+1]+be[v][t+1]-like;
	else
	  ps[u][v][t] = NEGATIVE_INFINITY;
      }
    }
  }
  /* Update new model. The model may have been partly updated by some training
     samples. */
  a = MATRIX(new_m->logA);
  b = VECTOR(new_m->logB);
  /* calculate B
     b[u] = gamma[u][1]
     - added scaling by sum of gammas to catch any numerical accuracy problems
     not log space here
   */
  for (u = 0; u<m->uu; u++) {
    /* This may be negative */
    b[u] += (Sc-postpC)*my_exp(VECTOR(gamma[u])[0])
      +my_exp(logD+VECTOR(m->logB)[u]);
  }
  /* calculate A matrix
   *                    sum(t = 1 ... tt-1, psi[u][v][t])
   * a[u][v] = -------------------------------------------------------
   *           sum(t = 1 ... tt-1, sum(w = 1 ... m->uu, psi[u][w][t]))
   * see note above about log space
   */
  for (u = 0; u<m->uu; u++) {
    for (v = 0; v<m->uu; v++) {
      /* This may be negative */
      dtf = 0.0;
      for(t = 0; t<tt-1; t++)
	dtf += my_exp(ps[u][v][t])*(Sc-postpC) + my_exp(logD+MATRIX(m->logA)[u][v]);
      a[u][v] += dtf;
    }
  }
  for (t = 0; t<tt; t++) {
    for (u = 0; u<m->uu; u++) VECTOR(gamma[u])[t] = my_exp(VECTOR(gamma[u])[t]);
  }
  return like;
}


/* Tam V'Nishlam Shevah L'El Borei Olam */
