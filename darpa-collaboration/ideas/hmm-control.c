/* LaHaShem HaAretz U'Mloah */

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <assert.h>
#include <string.h>
#include "hmm.h"
#include "hmm-rand.h"
#include "hmm-data.h"
#include "hmm-control.h"

static RVec **g_logL = NULL;
static RVec **g_gamma = NULL;
static RVec **g_gamma_r = NULL;
static int g_ll;
static int g_uu;
static RMat *g_lastData = NULL;

#define U(u) VECTOR(es)[u]
#define J(u) VECTOR(es2[j])[u]

/* -- utility functions -- */

void initGlobals(int ll, int uu, RMat *data)
{ int l, u;
  if (g_logL!=NULL)
  { for (l = 0; l<g_ll; l++)
    { for (u = 0; u<g_uu; u++)
      { freeRVec(g_logL[l][u]);
	freeRVec(g_gamma[l][u]);}
      free(g_logL[l]);
      free(g_gamma[l]);}
    for (u = 0; u<g_uu; u++) free(g_gamma_r[u]);
    free(g_logL);
    free(g_gamma);
    free(g_gamma_r);}
  g_logL = safe_malloc(sizeof *g_logL * ll);
  g_gamma = safe_malloc(sizeof *g_gamma * ll);
  g_gamma_r = safe_malloc(sizeof *g_gamma_r * uu);
  for (l = 0; l<ll; l++)
    { g_logL[l] = safe_malloc(sizeof *(g_logL[l]) * uu);
      g_gamma[l] = safe_malloc(sizeof *(g_gamma[l]) * uu);
    for (u = 0; u<uu; u++)
    { g_logL[l][u] = allocRVec(COLUMNS(data[l]));
      g_gamma[l][u] = allocRVec(COLUMNS(data[l]));}}
  for (u = 0; u<uu; u++)
    { g_gamma_r[u] = safe_malloc(sizeof *(g_gamma_r[u]) * ll);
      for (l = 0; l<ll; l++) g_gamma_r[u][l] = g_gamma[l][u];}
  g_uu = uu;
  g_ll = ll;}

/* allocModel - Allocates memory for a model with uu states and ii features. */
Model *allocModel(int ii, int uu)
{ Model *m;
  int u;
  if (ii<=0||uu<=0) panic("allocModel(): %s must be>0", ii<=0?"ii":"uu");
  m = safe_malloc(sizeof *m);
  m->ffm = safe_malloc(uu * sizeof *(m->ffm));
  m->uu = uu;
  m->hmm = allocHMM(uu);
  for (u = 0; u<uu; u++) m->ffm[u] = allocFFM(ii);
  m->n_roles = 0;
  m->name = NULL;
  m->pos = OTHER;
  return m;}

/* copyModel - Copy the contents of one model to another */
void copyModel(Model *dst_m, Model *src_m) {
  int u;
  assert(src_m&&dst_m);
  assert(src_m->ffm[0]->ii==dst_m->ffm[0]->ii);

  if(src_m->uu != dst_m->uu){
    freeModel(dst_m);
    dst_m = allocModel(src_m->ffm[0]->ii, src_m->uu);
  }
  copyHMM(dst_m->hmm, src_m->hmm);
  for (u = 0; u<src_m->uu; u++)
    copyFFM(dst_m->ffm[u], src_m->ffm[u]);
  if (dst_m->name) free(dst_m->name);
  dst_m->name = safe_malloc(strlen(src_m->name) + 1);
  strcpy(dst_m->name, src_m->name);
  dst_m->n_roles = src_m->n_roles;
  dst_m->pos = src_m->pos;
}

void uniformModel(Model *m, int ut){
  int u;
  constantHMM(m->hmm, ut);
  for (u = 0; u < m->uu; u ++)
    constantParams(m->ffm[u]);
}

void randomiseModel(Model *m, int ut)
{ int u;
  randomiseHMM(m->hmm, ut);
  for (u = 0; u < m->uu; u++)
    randomiseParams(m->ffm[u]);
}

void noiseModel(Model *m, int upper_triangular, Real delta){
  int u;
  noiseHMM(m->hmm, upper_triangular, delta);
  for (u = 0; u < m->uu; u ++)
    noiseParams(m->ffm[u], delta);
}

void smoothModel(Model *m, int upper_triangular, Real eps){
  int u;
  smoothHMM(m->hmm, upper_triangular, eps);
  for (u = 0; u < m->uu; u ++)
    smoothParams(m->ffm[u], eps);
}

int normalizeModel(Model *m, int upper_triangular, enum training_mode train_mode){
  assert(m);
  int u, *xu;
  xu = safe_malloc(m->uu * sizeof *xu);
  if (normaliseHMMlinear(m->hmm, upper_triangular, train_mode, xu) == FALSE){
    free(xu);
    return FALSE;
  }
  /* Remove unreachable states */
  for(u = 0; u < m->uu; u ++)
    if(xu[u]) break;
  if(u < m->uu){
    printf("Model (%s) has redundant state(s)\n", m->name);
    removeStates(m, xu, NULL);
  }
  free(xu);

  for (u = 0; u < m->uu; u ++)
    if (normalizeFFM(m->ffm[u], train_mode) == FALSE)
      return FALSE;
  return TRUE;
}

/* defineContFeat - Defines feature i to be a continuous feature whose
 *  value is in the range (-sigma, sigma] with p = 0.68.  Non-zero mean
 *  features should be translated appropiately.
 */
void defineContFeat(Model *m, int i, Real sigma)
{ struct ContFI *fi;
  int u, ii = m->ffm[0]->ii;
  if (i<0||i>=ii)
  { panic("defineContFeat(): i = %d is outside range [0,%d]", i, ii-1);}
  if (sigma<=0.0) panic("defineContFeat(): sigma = %g must be>0.0", sigma);
  for (u = 0; u<m->uu; u++)
  { fi = safe_malloc(sizeof *fi);
    fi->initialSigma = sigma;
    setFeatType(m->ffm[u], i, FT_CONTINUOUS, (void *)fi);}}

/* defineRadialFeat - Defines feature i to be a radial feature whose
 *  value is in the range [0, 2*pi).
 */
void defineRadialFeat(Model *m, int i)
{ int u, ii = m->ffm[0]->ii;
  if (i<0||i>=ii)
  { panic("defineRadialFeat(): i = %d is outside range [0,%d]", i, ii-1);}
  for (u = 0; u<m->uu; u++)
  { setFeatType(m->ffm[u], i, FT_RADIAL, NULL);}}

/* defineDiscreteFeat - Defines feature i to be a discrete feature whose
 *  value is in the range [0, n-1].
 */
void defineDiscreteFeat(Model *m, int i)
{ int u, ii = m->ffm[0]->ii;
  if (i<0||i>=ii)
  { panic("defineDiscreteFeat(): i = %d is outside range [0,%d]", i, ii-1);}
  for (u = 0; u<m->uu; u++)
  { setFeatType(m->ffm[u], i, FT_DISCRETE, NULL);}}

void displayModel(void *p, Model *m)
{ int u;
  FILE *f = (FILE *)p;
  fprintf(f, "========== Model Display ========\n");
  fprintf(f, "Model name: %s\n", m->name);
  fprintf(f, "Model role number (%d):\n", m->n_roles);
  fprintf(f, "\nModel part_of_speech: %s\n", model_pos_str(m->pos));
  displayHMM(f, m->hmm);
  for (u = 0; u<m->uu; u++)
  { fprintf(f, "-------\n");
    fprintf(f, "U = %3d\n", u);
    fprintf(f, "-------\n\n");
    displayFFM(f, m->ffm[u]);}}

void print_model(Model *m) {displayModel(stdout, m);}

void freeModel(Model *m)
{ int u;
  freeHMM(m->hmm);
  for (u = 0; u<m->uu; u++) freeFFM(m->ffm[u]);
  free(m->ffm);
  if (m->name) free(m->name);
  free(m);}

int isZeroModel(Model *m){
  assert(m);
  int u;
  if (isZeroHMMlinear(m->hmm) == 0)
    return 0;
  for (u = 0; u < m->uu; u ++)
    if (isZeroFFM(m->ffm[u]) == 0)
      return 0;
  return 1;
}

void zeroModel(Model *m){
  assert(m);
  int u;
  zeroHMMlinear(m->hmm);
  for (u = 0; u < m->uu; u ++)
    zeroFFM(m->ffm[u]);
}

void linear2logModel(Model *m){
  assert(m);
  linear2logHMM(m->hmm);
}

void log2linearModel(Model *m){
  assert(m);
  log2linearHMM(m->hmm);
}

Sentence initializeSentence(int ww, Model **ws){
  Sentence s = safe_malloc(sizeof *s);
  s->ww = ww;
  s->ws = safe_malloc(ww * sizeof *(s->ws));
  int w;
  for (w = 0; w < ww; w ++)
    s->ws[w] = ws[w];
  return s;
}

void freeSentence(Sentence s){
  free(s->ws);
  free(s);
}

/* -- processing functions -- */

/* logLike -
 *  Calculates log likelihood of entire model given the feature data.
 */
Real logLike(Model *m, RMat data){ 
  int u, uu = m->uu;
  RVec *logL;
  Real rv;
  logL = safe_malloc(uu * sizeof *logL);
  for (u = 0; u<uu; u++)
    { assert(ROWS(data)==m->ffm[u]->ii);
      logL[u] = allocRVec(COLUMNS(data));
      FFM_logL(m->ffm[u], logL[u], data);}
  rv = HMM_logL(m->hmm, logL);
  for (u = 0; u<uu; u++) freeRVec(logL[u]);
  free(logL);
  /* needs work: Why is rv divided by the number of features? */
  return rv/model_ii(m);}

/* logLike_with_box_scores -
 *  Calculates log likelihood of entire model given the feature data.
 */
Real logLike_with_box_scores(Model *m, RMat data, RMat score_mat){ 
  int u, uu = m->uu;
  RVec *logL;
  Real rv;
  Real **scores = MATRIX(score_mat);
  logL = safe_malloc(uu * sizeof *logL);
  for (u = 0; u<uu; u++){
    logL[u] = allocRVec(COLUMNS(data));
    FFM_logL_with_box_scores(m->ffm[u], logL[u], data,scores[u]);}
  rv = HMM_logL(m->hmm, logL);
  for (u = 0; u<uu; u++) freeRVec(logL[u]);
  free(logL);
  /* needs work: Why is rv divided by the number of features? */
  return rv/model_ii(m);}

/* best_state_sequence -
 *  Calculates the best state sequence of entire model given the feature data.
 */
int *best_state_sequence(Model *m, RMat data){ 
  int u, uu = m->uu, *rv;
  RVec *logL;
  logL = safe_malloc(uu * sizeof *logL);
  for (u = 0; u<uu; u++)
  { assert(ROWS(data)==m->ffm[u]->ii);
    logL[u] = allocRVec(COLUMNS(data));
    FFM_logL(m->ffm[u], logL[u], data);}
  rv = HMM_best_state_sequence(m->hmm, logL);
  for (u = 0; u<uu; u++) freeRVec(logL[u]);
  free(logL);
  return rv;}

double** state_probabilities(Model *m, RMat data){ 
  int u, uu = m->uu, t;
  RVec *gammas;
  RVec *logL;
  double **rv;
  logL = safe_malloc(uu * sizeof *logL);
  gammas = safe_malloc(uu * sizeof *gammas);
  rv = safe_malloc(COLUMNS(data) * sizeof *rv);
  for (u = 0; u<uu; u++)
  { assert(ROWS(data)==m->ffm[u]->ii);
    logL[u] = allocRVec(COLUMNS(data));
    gammas[u] = allocRVec(COLUMNS(data));

    FFM_logL(m->ffm[u], logL[u], data);}
  HMM_calcGammas(m->hmm,logL,gammas);

  for (t=0; t<COLUMNS(data); t++)
    { rv[t] = safe_malloc(uu * sizeof *(rv[t]));
    for(u=0; u<uu; u++)
     {
       rv[t][u] = VECTOR(gammas[u])[t];}}
  // copy the data into the array which is going to be passed to scheme
  for (u = 0; u<uu; u++)
   { freeRVec(logL[u]);
     freeRVec(gammas[u]);}
  free(logL);
  free(gammas);
  return rv;}

double** state_probabilities_with_box_scores(Model *m, RMat data, RMat score_mat){ 
  int u, uu = m->uu, t;
  RVec *gammas;
  RVec *logL;
  double **rv;
  Real **scores = MATRIX(score_mat);
  logL = safe_malloc(uu * sizeof *logL);
  gammas = safe_malloc(uu * sizeof *gammas);
  rv = safe_malloc(COLUMNS(data) * sizeof *rv);
  for (u = 0; u<uu; u++)
  { assert(ROWS(data)==m->ffm[u]->ii);
    logL[u] = allocRVec(COLUMNS(data));
    gammas[u] = allocRVec(COLUMNS(data));

    FFM_logL_with_box_scores(m->ffm[u], logL[u], data,scores[u]);}
  HMM_calcGammas(m->hmm,logL,gammas);

  for (t=0; t<COLUMNS(data); t++)
    { rv[t] = safe_malloc(uu * sizeof *(rv[t]));
    for(u=0; u<uu; u++)
     {
       rv[t][u] = VECTOR(gammas[u])[t];}}
  // copy the data into the array which is going to be passed to scheme
  for (u = 0; u<uu; u++)
   { freeRVec(logL[u]);
     freeRVec(gammas[u]);}
  free(logL);
  free(gammas);
  return rv;}


void force_init_globals(void) {g_lastData = NULL;}

void compute_posterior(Model **m, RMat *data, Real *prior, int *c_ls, int ll,
		       int cc, enum training_mode training_mode,
		       /* outputs */
		       Real *objective_function, Real *auxiliary, Real **postpC) {
  int l, c;
  Real postp_sum;
  assert(postpC);
  for (l = 0; l<ll; l++)
    for (c = 0; c<cc; c++)
      postpC[c][l] = logLike(m[c], data[l])+my_log(prior[c]);

  *objective_function = 0.0;
  *auxiliary = 0.0;
  for (l = 0; l<ll; l++) {
    postp_sum = NEGATIVE_INFINITY;
    for (c = 0; c<cc; c++) postp_sum = add_exp(postp_sum, postpC[c][l]);
    for (c = 0; c<cc; c++) {
	switch (training_mode) {
	case HMM_ML:
	  if(c==c_ls[l]) *objective_function += postpC[c][l];
	  if(postp_sum!=NEGATIVE_INFINITY)
	    postpC[c][l] -= postp_sum;
	  if(c==c_ls[l]) *auxiliary += postpC[c][l];
	  break;
	case HMM_DT:
	  if(c==c_ls[l]) *auxiliary += postpC[c][l];
	  if(postp_sum!=NEGATIVE_INFINITY)
	    postpC[c][l] -= postp_sum;
	  if(c==c_ls[l]) *objective_function += postpC[c][l];
	  break;
	default: panic("unrecognized training mode");
	}
    }
  }
}

/* update - Performs a single update on the HMM model for the given data. */
int update(Model **m, Hmm **tmp_hmm, RMat *data, Real **postpC, Real log_D,
	   int *c_ls, int ll, int cc, enum training_mode training_mode,
	   int upper_triangular) {
  /* tmp_hmm[c] is scratch space. It must have at least as many states as
     m[c]->hmm. */
  int c, l, u;
  int *xu;
  for (c = 0; c<cc; c++) {
    if (g_lastData!=data||ll>g_ll||m[c]->uu>g_uu) {
      g_lastData = data;
      initGlobals(ll, m[c]->uu, data);
    }
    zeroHMMlinear(tmp_hmm[c]);
    for (l = 0; l<ll; l++) {
      switch (training_mode) {
      case HMM_ML:
	if (c!=c_ls[l]) continue;
	for (u = 0; u<m[c]->uu; u++) {
	  FFM_logL(m[c]->ffm[u], g_logL[l][u], data[l]);
	}
	HMM_updateModel(m[c]->hmm, tmp_hmm[c], g_logL[l], g_gamma[l], log_D,
			0.0, -1, -1, training_mode);
	break;
      case HMM_DT:
	for (u = 0; u<m[c]->uu; u++) {
	  FFM_logL(m[c]->ffm[u], g_logL[l][u], data[l]);
	}
	HMM_updateModel(m[c]->hmm, tmp_hmm[c], g_logL[l], g_gamma[l], log_D,
			my_exp(postpC[c][l]), c, c_ls[l], training_mode);
	break;
      default: panic("unrecognized training mode");
      }
    }
    xu = safe_malloc(sizeof *xu * m[c]->uu);
    if (!normaliseHMMlinear(tmp_hmm[c], upper_triangular, training_mode, xu)) {
      assert(training_mode == HMM_DT);
      free(xu);
      return FALSE;
    }
    copyHMM(m[c]->hmm, tmp_hmm[c]);
    for (u = 0; u<m[c]->uu; u++) {
      switch (training_mode) {
      case HMM_ML:
	assert(FFM_maximise(m[c]->ffm[u], data, g_gamma_r[u], ll, log_D,
			    NULL, c, c_ls));
	break;
      case HMM_DT:
	if (!FFM_maximise(m[c]->ffm[u], data, g_gamma_r[u], ll, log_D,
			  postpC[c], c, c_ls)){
	  free(xu);
	  return FALSE;
	}
	break;
      default: panic("unrecognized training mode");
      }
    }
    /* Remove redundant states by shifting the memory if necessary. */
    for(u = 0; u < m[c]->uu; u ++)
      if(xu[u]) break;
    if(u < m[c]->uu)
      removeStates(m[c], xu, tmp_hmm[c]);
    free(xu);
  }
  return TRUE;
}

void decode_box_states(int j, int ii, int nTracks, int *js){
  int i;
  for (i = nTracks - 1; i >= 0; i --){
    js[i] = j % ii;
    j /= ii;
  }
}

void compute_sentence_priors(Sentence *ss, int nn, int tt, Real *priors){
  int n, w, i, uu;
  Ffm *ffm;
  DParam *p;
  Real prior_weight = 1.0 / 1.8;
  /* Compute the prior for each sentence */
  for (n = 0; n < nn; n ++){
    priors[n] = 0;  /* Log space */
    for (w = 0; w < ss[n]->ww; w ++){
      ffm = ss[n]->ws[w]->ffm[0];
      for (i = 0; i < ffm->ii; i ++){
    	p = (DParam *)(ffm->p[i]);
    	priors[n] += tt * my_log(p->kk);
      }
      uu = VLENGTH(ss[n]->ws[w]->hmm->logB);
      priors[n] += tt * my_log(uu);
    }
    /* Add a weight for the prior */
    priors[n] *= prior_weight;
  }
}

void compute_eligible_states
(Sentence *ss, int *nTracks, int nn, IVec **roles_multiple, BoxesMovie bm,
 enum model_constraint model_constraint, IVec *eligible_states, IVec **secondary_eligible_states){

  int *js, nRoles, w, *r, *eligible, n, jjj, u, i, ii;
  int eligible_tot, tt, t, cnt, *ks, jj, j, k;
  Box *d1, *d2, **ds;
  IVec es, *es2, *roles;
  ii = bm->ii;
  tt = bm->tt;
  /* Our implementation assumes that the order of box models in each frame is
     coherent
   */
  ds = bm->ds[0];
  for (t = 1; t < tt; t ++)
    for (i = 0; i < ii; i ++)
      assert(box_similarity((bm->ds[t])[i], ds[i]) == 0);
  for (n = 0; n < nn; n ++){
    jjj = 1;
    for (i = 0; i < nTracks[n]; i ++) jjj *= ii;
    js = safe_malloc(nTracks[n] * sizeof *js);
    eligible = safe_malloc(jjj * sizeof *eligible);
    roles = roles_multiple[n];
    eligible_tot = 0;
    for (u = 0; u < jjj; u ++){
      eligible[u] = 1;
      if (model_constraint == NO_DUPLICATE_MODELS){
	decode_box_states(u, ii, nTracks[n], js);
	for (w = 0; w < ss[n]->ww; w ++){
	  assert(ss[n]->ws[w]->n_roles == VLENGTH(roles[w]));
	  nRoles = VLENGTH(roles[w]);
	  assert(nRoles == 1 || nRoles == 2); /* Not apply to nRoles >= 3 */
	  if (nRoles == 1) continue;
	  r = VECTOR(roles[w]);
	  assert(r[0] != r[1]);
	  d1 = ds[js[r[0]]];
	  d2 = ds[js[r[1]]];
	  if (box_similarity(d1, d2) == 0){
	    eligible[u] = 0;
	    break;
	  }
	}
      }
      if (eligible[u] == 1) eligible_tot ++;
    }
    eligible_states[n] = allocIVec(eligible_tot);
    es = eligible_states[n];
    cnt = 0;
    for (u = 0; u < jjj; u ++)
      if (eligible[u] == 1){
	U(cnt) = u;
	cnt ++;
      }
    free(eligible);
    /* Now for each box state, compute the previously eligible box states such
       that the box similarity score between each eligible state and the
       current state is not -inf
    */
    jj = VLENGTH(es);
    ks = safe_malloc(nTracks[n] * sizeof *ks);
    secondary_eligible_states[n] = safe_malloc(jj * sizeof *(secondary_eligible_states[n]));
    es2 = secondary_eligible_states[n];
    eligible = safe_malloc(jj * sizeof *eligible);
    for (j = 0; j < jj; j ++){
      decode_box_states(U(j), ii, nTracks[n], js);
      eligible_tot = 0;
      for (k = 0; k < jj; k ++){
	eligible[k] = 1;
	decode_box_states(U(k), ii, nTracks[n], ks);
	for (i = 0; i < nTracks[n]; i ++)
	  if (box_similarity(ds[js[i]], ds[ks[i]]) == NEGATIVE_INFINITY){
	    eligible[k] = 0;
	    break;
	  }
	if (eligible[k] == 1)
	  eligible_tot ++;
      }
      es2[j] = allocIVec(eligible_tot);
      cnt = 0;
      for (k = 0; k < jj; k ++)
	if (eligible[k] == 1){
	  J(cnt) = k;
	  cnt ++;
	}
    }
    free(eligible);
    free(ks);
    free(js);
  }
}

void compute_crossed_likelihood
(IVec es, int vv, Sentence s, int nTracks, IVec *roles, BoxesMovie bm, Flow **flow_movie, 
 Real scale, char **objects, int ol, FeatureMedoid **fms, int mn, Real **outL){

#define OUTPUT_MODEL(s, w, u) \
  s->ws[w]->ffm[u]

  int *js, *us, nRoles, w, *r, t, tt, ii, j, u, jj, x;
  Hmm **hmms;
  Box *d1, *d2, *d3, **ds;
  RVec *data;
  Features f;
  hmms = safe_malloc(s->ww * sizeof *hmms);
  us = safe_malloc(s->ww * sizeof *us);
  js = safe_malloc(nTracks * sizeof *js);
  data = safe_malloc(s->ww * sizeof *data);
  tt = bm->tt;
  ii = bm->ii;
  jj = VLENGTH(es);
  for (w = 0; w < s->ww; w ++){
    hmms[w] = s->ws[w]->hmm;
    assert(s->ws[w]->n_roles == VLENGTH(roles[w]));
  }
  for (t = 0; t < tt; t ++){
    ds = bm->ds[t];
    for (j = 0; j < jj; j ++){
      decode_box_states(U(j), ii, nTracks, js);
      for (w = 0; w < s->ww; w ++){
	/* For model w in the sentence s, pick the tracks according to its roles */
	nRoles = VLENGTH(roles[w]);
	/* For now, each model has no more than three roles */
	assert(nRoles <= 3 && nRoles >= 1);  
	r = VECTOR(roles[w]);
	d1 = ds[js[r[0]]];
	d2 = NULL;
	d3 = NULL;
	if (nRoles >= 2){
	  assert(r[0] != r[1]);
	  d2 = ds[js[r[1]]];
	}
	if (nRoles >= 3){
	  assert(r[1] != r[2] && r[0] != r[2]);
	  d3 = ds[js[r[2]]];
	}
	/* Compute a feature vector for this model */
	f = new_feature(s->ws[w]->pos, d1, d2, d3, objects, ol, flow_movie[t], scale, fms, mn);
	data[w] = allocRVec(f->ii);
	memcpy(VECTOR(data[w]), f->v[0], f->ii * sizeof(Real));
	freeFeatures(f);
      }
      for (u = 0; u < vv; u ++){
	HMM_decodeXStates(us, hmms, u, s->ww);
	x = u * jj + j;
	outL[t][x] = 0;
	for (w = 0; w < s->ww; w ++)
	  outL[t][x] += FFM_logL_one(OUTPUT_MODEL(s, w, us[w]), data[w]);
      }
      for (w = 0; w < s->ww; w ++)
	freeRVec(data[w]);
    }
  }
  free(js);
  free(hmms);
  free(us);
  free(data);
  return;
}

/* Similar to viterbi tracker The difference is the MAX replaced with SUM, and
   we only return the exhaustive score 
*/
Real viterbi_tracker_score(Real ***bps, Real **bss, int nTracks, int ii, int tt, IVec es, IVec *es2){
  int *js = safe_malloc(nTracks * sizeof *js);
  int *ks = safe_malloc(nTracks * sizeof *ks);
  Real **alpha;
  Real pair, single, overall_score;
  int i, t, idx = 0, jj, kk, j, k;

  jj = VLENGTH(es);
  alpha = safe_malloc(jj * sizeof *alpha); /* log space */;
  /* t = 0 */
  for (j = 0; j < jj; j ++){
    alpha[j] = safe_malloc(2 * sizeof *(alpha[j]));
    decode_box_states(U(j), ii, nTracks, js);
    alpha[j][0] = 0;
    for (i = 0; i < nTracks; i ++)
      alpha[j][0] += bss[0][js[i]];
  }
  /* t >= 1 */
  for (t = 1; t < tt; t ++){
    idx = t % 2;
    for (j = 0; j < jj; j ++){
      decode_box_states(U(j), ii, nTracks, js);
      alpha[j][idx] = NEGATIVE_INFINITY;
      kk = VLENGTH(es2[j]);
      for (k = 0; k < kk; k ++){
	decode_box_states(U(J(k)), ii, nTracks, ks);
	pair = 0;
	for (i = 0; i < nTracks; i ++)
	  pair += bps[t-1][ks[i]][js[i]];
	assert(pair != NEGATIVE_INFINITY);
	alpha[j][idx] = add_exp(alpha[j][idx], alpha[J(k)][1-idx] + pair);
      }
      single = 0;
      for (i = 0; i < nTracks; i ++)
	single += bss[t][js[i]];
      alpha[j][idx] += single;
    }
  }
  overall_score = NEGATIVE_INFINITY;
  for (j = 0; j < jj; j ++)
    overall_score = add_exp(overall_score, alpha[j][idx]);
  /* Clean up */
  free(js);
  free(ks);
  for (j = 0; j < jj; j ++)
    free(alpha[j]);
  free(alpha);
  return overall_score;
}

void sentence_calc_alpha(IVec es, IVec *es2, int vv, int ii, int tt, int nTracks, Real **a, Real *b,
			 Real ***bps, Real **bss, Real **outL, Real **alpha){
  int *js, *ks, u, v, i, t, jj, x, y, j, k, kk;
  Real single, pair;
  jj = VLENGTH(es);
  js = safe_malloc(nTracks * sizeof *js);
  ks = safe_malloc(nTracks * sizeof *ks);
  /* t = 0 */
  for (j = 0; j < jj; j ++)
    for (u = 0; u < vv; u ++){
      x = u * jj + j;
      decode_box_states(U(j), ii, nTracks, js);
      /* Initial state probability times output probability */
      alpha[0][x] = b[u] + outL[0][x];
      /* Box single score at frame 0 */
      for (i = 0; i < nTracks; i ++)
	alpha[0][x] += bss[0][js[i]];
  }
  /* t >= 1 */
  for (t = 1; t < tt; t ++){
    for (j = 0; j < jj; j ++){
      decode_box_states(U(j), ii, nTracks, js);
      single = 0;
      for (i = 0; i < nTracks; i ++)
	single += bss[t][js[i]];
      kk = VLENGTH(es2[j]);
      for (u = 0; u < vv; u ++){
	x = u * jj + j;
	alpha[t][x] = NEGATIVE_INFINITY;
	for (k = 0; k < kk; k ++){
	  decode_box_states(U(J(k)), ii, nTracks, ks);
	  pair = 0;
	  for (i = 0; i < nTracks; i ++)
	    pair += bps[t-1][ks[i]][js[i]];
	  assert(pair != NEGATIVE_INFINITY);
	  for (v = 0; v < vv; v ++){
	    y = v * jj + J(k);
	    alpha[t][x] = add_exp(alpha[t][x], alpha[t-1][y] + pair + a[v][u]);
	  }
	}
	alpha[t][x] += single + outL[t][x];
      }
    }
  }
  free(js);
  free(ks);
}

void sentence_calc_beta(IVec es, IVec *es2, int vv, int ii, int tt, int nTracks, Real **a,
			Real ***bps, Real **bss, Real **outL, Real **beta){
  int *js, *ks, u, v, i, t, jj, x, y, j, k, kk;
  Real single, pair;
  jj = VLENGTH(es);
  js = safe_malloc(nTracks * sizeof *js);
  ks = safe_malloc(nTracks * sizeof *ks);
  /* t = tt - 1 */
  memset(beta[tt - 1], 0, jj * vv * sizeof(Real));
  /* t <= tt - 2 */
  for (t = tt - 2; t >= 0; t --){
    for (j = 0; j < jj; j ++){
      decode_box_states(U(j), ii, nTracks, js);
      kk = VLENGTH(es2[j]);
      for (u = 0; u < vv; u ++){
	x = u * jj + j;
	beta[t][x] = NEGATIVE_INFINITY;
	for (k = 0; k < kk; k ++){
	  decode_box_states(U(J(k)), ii, nTracks, ks);
	  single = pair = 0;
	  for (i = 0; i < nTracks; i ++){
	    pair += bps[t][js[i]][ks[i]];
	    single += bss[t+1][ks[i]];
	  }
	  assert(pair != NEGATIVE_INFINITY);
	  for (v = 0; v < vv; v ++){
	    y = v * jj + J(k);
	    beta[t][x] = add_exp(beta[t][x], beta[t+1][y] + pair
				 + a[u][v] + outL[t+1][y] + single);
	  }
	}
      }
    }
  }
  free(js);
  free(ks);
}

void sentence_calc_gamma(int uu, int tt, Real **alpha, Real **beta, Real like, Real **gamma){
  int t, u;
  for (t = 0; t < tt; t ++)
    for (u = 0; u < uu; u ++)
      if (like != NEGATIVE_INFINITY)
	gamma[t][u] = alpha[t][u] + beta[t][u] - like;
      else
	gamma[t][u] = NEGATIVE_INFINITY;
}

void compute_sentence_statistics
(Sentence *ss, xHmm **xhmms, int *nTracks, int nn, IVec **roles_multiple,
 BoxesMovie bm, Flow **flow_movie, int fl_tt, Real scale, Real *priors,
 char **objects, int ol, FeatureMedoid **fms, int mn, IVec *eligible_states,
 IVec **secondary_eligible_states,
 /* Ouput */
 Real ***xGamma, Real ***xAlpha, Real ***xBeta,
 Real ***xState_like, Real *like, Real *posteriors, Real *total_post){
 
  int n, tt, u, ii, vv, jj, uu;
  Real **gamma, **alpha, **beta, **outL;
  Real **a, *b, ***bps, **bss, *track_score;
  IVec es, *es2;
  tt = bm->tt;
  ii = bm->ii;
  assert(tt == fl_tt);
  track_score = safe_malloc(nn * sizeof *track_score);
  for (n = 0; n < nn; n ++){
    gamma = xGamma[n];
    alpha = xAlpha[n];
    beta = xBeta[n];
    outL = xState_like[n];
    a = MATRIX(xhmms[n]->m->logA);
    b = VECTOR(xhmms[n]->m->logB);
    bps = MATRIX(xhmms[n]->bps);
    bss = MATRIX(xhmms[n]->bss);
    es = eligible_states[n];
    es2 = secondary_eligible_states[n];
    jj = VLENGTH(es);
    vv = xhmms[n]->m->uu;
    uu = jj * vv;
    /* Compute crossed likelihood */
    compute_crossed_likelihood(es, vv, ss[n], nTracks[n], roles_multiple[n], bm,
			       flow_movie, scale, objects, ol, fms, mn, outL);
    /* Calculate the overall track score for this sentence */
    track_score[n] = viterbi_tracker_score(bps, bss, nTracks[n], ii, tt, es, es2);
    /* Calculate gamma and sentence likelihood */
    sentence_calc_alpha(es, es2, vv, ii, tt, nTracks[n], a, b, bps, bss, outL, alpha);
    sentence_calc_beta(es, es2, vv, ii, tt, nTracks[n], a, bps, bss, outL, beta);
    /* NOTE: alpha and beta not normalized here,
       the normalization factor will be cancelled by substracting 'like' */
    like[n] = NEGATIVE_INFINITY;
    for (u = 0; u < uu; u ++)
      like[n] = add_exp(like[n], alpha[tt-1][u]);
    /* The positive sample should always not be -inf */
    if (n == 0)
      assert(like[n] != NEGATIVE_INFINITY);
    sentence_calc_gamma(uu, tt, alpha, beta, like[n], gamma);
  }
  /* Compute posterior for each sentence */
  *total_post = NEGATIVE_INFINITY;
  for (n = 0; n < nn; n ++){
    posteriors[n] = priors[n] + (like[n] - track_score[n]);
    *total_post = add_exp(*total_post, posteriors[n]);
  }
  /* If this assertion fails, check whether the positive sentence is correct
     Or check whether correct object models are provided for this video
   */
  assert(*total_post != NEGATIVE_INFINITY);
  for (n = 0; n < nn; n ++)
    posteriors[n] -= *total_post;

  free(track_score);
}

/* scratch model should be in the original space */
void HMM_updateXModel(Sentence s, Sentence scratch, xHmm *xhmm, IVec es, IVec *es2, int nTracks,
		      Real **alpha, Real **beta, Real **state_like, Real *gamma0, Real like,
		      Real posterior, Real log_D, enum training_mode training_mode, int positive){
#define SENTENCE_HMM_A(s, w) \
  s->ws[w]->hmm->logA
#define SENTENCE_HMM_B(s, w) \
  s->ws[w]->hmm->logB

  int t, u, v, i, w, tt, ww, ii, *js, *ks, *us, *vs, kk, jj, j, k, x, y, vv;
  Real **a, **xa, **na, *b, *nb, ***bps, **bss, psi, single, pair, sign;
  Hmm **hmms;
  tt = xhmm->tt;
  ii = xhmm->ii;
  xa = MATRIX(xhmm->m->logA);
  bps = MATRIX(xhmm->bps);
  bss = MATRIX(xhmm->bss);
  jj = VLENGTH(es);
  vv = xhmm->m->uu;
  ww = s->ww;
  assert(ww == scratch->ww);
  if (training_mode == HMM_ML)
    sign = 1;
  else
    sign = positive - posterior;
  js = safe_malloc(nTracks * sizeof *js);
  ks = safe_malloc(nTracks * sizeof *ks);
  us = safe_malloc(ww * sizeof *us);
  vs = safe_malloc(ww * sizeof *vs);
  hmms = safe_malloc(ww * sizeof *hmms);
  for (w = 0; w < ww; w ++)
    hmms[w] = s->ws[w]->hmm;
  for (t = 0; t < tt - 1; t ++){
    for (j = 0; j < jj; j ++){
      decode_box_states(U(j), ii, nTracks, js);
      kk = VLENGTH(es2[j]);
      for (u = 0; u < vv; u ++){
	HMM_decodeXStates(us, hmms, u, ww);
	x = u * jj + j;
	/* Update initial probability at time 0 */
	if (t == 0){
	  for (w = 0; w < ww; w ++){
	    nb = VECTOR(SENTENCE_HMM_B(scratch, w));
	    /* b at state us[w] */
	    assert(us[w] >= 0 && us[w] < scratch->ws[w]->uu);
	    nb[us[w]] += sign * my_exp(gamma0[x]);
	  }
	}
	for (k = 0; k < kk; k ++){
	  decode_box_states(U(J(k)), ii, nTracks, ks);
	  pair = single = 0;
	  for (i = 0; i < nTracks; i ++){
	    pair += bps[t][js[i]][ks[i]];
	    single += bss[t+1][ks[i]];
	  }
	  assert(pair != NEGATIVE_INFINITY);
	  for (v = 0; v < vv; v ++){
	    HMM_decodeXStates(vs, hmms, v, ww);
	    y = v * jj + J(k);
	    for (w = 0; w < ww; w ++){
	      na = MATRIX(SENTENCE_HMM_A(scratch, w));
	      if (like != NEGATIVE_INFINITY)
		psi = alpha[t][x] + (xa[u][v]+pair)
		  + (state_like[t+1][y]+single) + beta[t+1][y] - like;
	      else
		psi = NEGATIVE_INFINITY;
	      assert(vs[w] >= 0 && vs[w] < scratch->ws[w]->uu);
	      na[us[w]][vs[w]] += sign * my_exp(psi);
	    }
	  }
	}
      }
    }
    /* Now add the damping term */
    if (training_mode == HMM_DT){
      for (w = 0; w < ww; w ++){
    	na = MATRIX(SENTENCE_HMM_A(scratch, w));
    	nb = VECTOR(SENTENCE_HMM_B(scratch, w));
    	a = MATRIX(SENTENCE_HMM_A(s, w));
    	b = VECTOR(SENTENCE_HMM_B(s, w));
    	for (u = 0; u < scratch->ws[w]->uu; u ++){
    	  if (t == 0)
    	    nb[u] += my_exp(log_D + b[u]);
    	  for (v = 0; v < scratch->ws[w]->uu; v ++)
    	    na[u][v] += my_exp(log_D + a[u][v]);
    	}
      }
    }
  }
  /* Clean up */
  free(js);
  free(ks);
  free(us);
  free(vs);
  free(hmms);
}

void FFM_updateXModel
(Sentence s, Sentence scratch, IVec *roles, Real **gamma, int vv, IVec es, int nTracks,
 BoxesMovie bm, Flow **flow_movie, Real scale, Real posterior, Real log_D, 
 enum training_mode training_mode, int positive, char **objects, int ol, FeatureMedoid **fms, int mn){

#define SENTENCE_FFM_P(s, w, u, i) \
  s->ws[w]->ffm[u]->p[i]

  int tt, t, i, u, j, jj, *js, w, ww, ii, *us, nRoles, *r, x, z;
  Real sign, D = my_exp(log_D);
  Box **ds, *d1, *d2, *d3;
  Hmm **hmms;
  Features *fs;
  DParam *p, *np;
  tt = bm->tt;
  ii = bm->ii;
  ww = s->ww;
  assert(ww == scratch->ww);
  jj = VLENGTH(es);
  js = safe_malloc(nTracks * sizeof *js);
  us = safe_malloc(ww * sizeof *us);
  hmms = safe_malloc(ww * sizeof *hmms);
  fs = safe_malloc(ww * sizeof *fs);
  if (training_mode == HMM_ML)
    sign = 1;
  else
    sign = positive - posterior;
  for (w = 0; w < ww; w ++){
    hmms[w] = s->ws[w]->hmm;
    assert(s->ws[w]->n_roles == VLENGTH(roles[w]));
  }

  for (t = 0; t < tt; t ++){
    ds = bm->ds[t];
    for (j = 0; j < jj; j ++){
      decode_box_states(U(j), ii, nTracks, js);
      for (w = 0; w < ww; w ++){
	nRoles = VLENGTH(roles[w]);
	assert(nRoles <= 3 && nRoles >= 1);
	r = VECTOR(roles[w]);
	d1 = ds[js[r[0]]];
	d2 = NULL;
	d3 = NULL;
	if (nRoles >= 2){
	  assert(r[0] != r[1]);
	  d2 = ds[js[r[1]]];
	}
	if (nRoles >= 3){
	  assert(r[1] != r[2] && r[0] != r[2]);
	  d3 = ds[js[r[2]]];
	}
	/* Compute the feature for this model at time t */
	fs[w] = new_feature(s->ws[w]->pos, d1, d2, d3, objects, ol, flow_movie[t],
			    scale, fms, mn);
	assert(fs[w]->ii == s->ws[w]->ffm[0]->ii);
      }
      for (u = 0; u < vv; u ++){
	HMM_decodeXStates(us, hmms, u, ww);
	x = u * jj + j;
	for (w = 0; w < ww; w ++){
	  for (i = 0; i < fs[w]->ii; i ++){
	    assert(s->ws[w]->ffm[us[w]]->ft[i] == FT_DISCRETE);
	    np = SENTENCE_FFM_P(scratch, w, us[w], i);
	    z = (int)(fs[w]->v[0][i]);
	    assert(z >= 0 && z < np->kk);
	    np->p[z] += sign * my_exp(gamma[t][x]);
	  }
	}
      }
      for (w = 0; w < ww; w ++)
      	freeFeatures(fs[w]);
    }
    /* Now add the damping term */
    if (training_mode == HMM_DT){
      for (w = 0; w < ww; w ++){
	for (u = 0; u < scratch->ws[w]->uu; u ++)
	  for (i = 0; i < scratch->ws[w]->ffm[u]->ii; i ++){
	    p = SENTENCE_FFM_P(s, w, u, i);
	    np = SENTENCE_FFM_P(scratch, w, u, i);
	    assert(np->kk == p->kk);
	    for (z = 0; z < np->kk; z ++)
	      np->p[z] += D * p->p[z];
	  }
      }
    }
  }
  /* Clean up */
  free(js);
  free(us);
  free(hmms);
  free(fs);
}

Real viterbi_sentence_tracker
(Sentence s, xHmm *xhmm, int nTracks, IVec *roles, BoxesMovie bm, Flow **flow_movie,
 int fl_tt, Real scale, Real prior, char **objects, int ol, FeatureMedoid **fms,
 int mn, IVec es, IVec *es2, int sum_or_max, int final_state, int *box_sequence){
  
  int t, tt, j, jj, k, kk, u, vv, uu, v, w, x, y, *js, *ks, **path, current_state, ii, i, *us;
  Real **outL, **a, *b, ***bps, **bss, track_score, **alpha, return_value, transition_score, single, pair;
  Hmm **hmms;
  tt = bm->tt;
  ii = bm->ii;
  assert(tt = fl_tt);
  jj = VLENGTH(es);
  vv = xhmm->m->uu;
  uu = jj * vv;
  alpha = safe_malloc(tt * sizeof *alpha);  /* alpha can be just a scrolling array */
  outL = safe_malloc(tt * sizeof *outL);
  path = safe_malloc(tt * sizeof *path);
  for (t = 0; t < tt; t ++){
    alpha[t] = safe_malloc(uu * sizeof *(alpha[t]));
    outL[t] = safe_malloc(uu * sizeof *(outL[t]));
    path[t] = safe_malloc(uu * sizeof *(path[t]));
  }
  a = MATRIX(xhmm->m->logA);
  b = VECTOR(xhmm->m->logB);
  bps = MATRIX(xhmm->bps);
  bss = MATRIX(xhmm->bss);
  compute_crossed_likelihood(es, vv, s, nTracks, roles, bm, flow_movie, scale,
			     objects, ol, fms, mn, outL);
  track_score = viterbi_tracker_score(bps, bss, nTracks, ii, tt, es, es2);
  /* Start tracking */
  js = safe_malloc(nTracks * sizeof *js);
  ks = safe_malloc(nTracks * sizeof *ks);
  /* t = 0 */
  for (j = 0; j < jj; j ++){
    decode_box_states(U(j), ii, nTracks, js);
    single = 0;
    /* Box single score at frame 0 */
    for (i = 0; i < nTracks; i ++)
      single += bss[0][js[i]];
    for (u = 0; u < vv; u ++){
      x = u * jj + j;
      /* Initial state probability times output probability */
      alpha[0][x] = b[u] + outL[0][x] + single;
      path[0][x] = -1;
    }
  }
  /* t >= 1 */
  for (t = 1; t < tt; t ++){
    for (j = 0; j < jj; j ++){
      decode_box_states(U(j), ii, nTracks, js);
      single = 0;
      for (i = 0; i < nTracks; i ++)
	single += bss[t][js[i]];
      kk = VLENGTH(es2[j]);
      for (u = 0; u < vv; u ++){
	x = u * jj + j;
	alpha[t][x] = NEGATIVE_INFINITY;
	for (k = 0; k < kk; k ++){
	  decode_box_states(U(J(k)), ii, nTracks, ks);
	  pair = 0;
	  for (i = 0; i < nTracks; i ++)
	    pair += bps[t-1][ks[i]][js[i]];
	  assert(pair != NEGATIVE_INFINITY);
	  for (v = 0; v < vv; v ++){
	    y = v * jj + J(k);
	    transition_score = alpha[t-1][y] + pair + a[v][u];
	    if (sum_or_max == 0)
	      alpha[t][x] = add_exp(alpha[t][x], transition_score);
	    else{  /* track the state sequence */
	      if (alpha[t][x] < transition_score){
		alpha[t][x] = transition_score;
		path[t][x] = y;
	      }
	    }
	  }
	}
	alpha[t][x] += single + outL[t][x];
      }
    }
  }
  us = safe_malloc(s->ww * sizeof *us);
  hmms = safe_malloc(s->ww * sizeof *hmms);
  for (w = 0; w < s->ww; w ++)
    hmms[w] = s->ws[w]->hmm;

  return_value = NEGATIVE_INFINITY;
  /* compute likelihood */
  if (sum_or_max == 0){
    for (u = 0; u < uu; u ++){
      v = u / jj;
      HMM_decodeXStates(us, hmms, v, s->ww);
      if (final_state){
	for (w = 0; w < s->ww; w ++)
	  /* For multi-state hmms, if the state stays at the initial, ignore */
	  if (hmms[w]->uu > 1 && us[w] == 0)
	    break;
	if (w < s->ww) continue;
      }
      return_value = add_exp(return_value, alpha[tt - 1][u]);
    }
  }
  /* compute most possible state sequence */
  else{
    current_state = -1;
    for (u = 0; u < uu; u ++){
      v = u / jj;
      HMM_decodeXStates(us, hmms, v, s->ww);
      if (final_state){
	for (w = 0; w < s->ww; w ++)
	  /* For multi-state hmms, if the state stays at the initial, ignore */
	  if (hmms[w]->uu > 1 && us[w] == 0)
	    break;
	if (w < s->ww) continue;
      }
      if (return_value < alpha[tt - 1][u]){
	return_value = alpha[tt - 1][u];
        current_state = u;
      }
    }
    /* No track eligible */
    if (current_state == -1){
      printf("viterbi-sentence-track-in-c, all costs were infinity\n");
    }
    else{
    /* Get the box sequence */
      for (t = tt - 1; t >= 0; t --){
	box_sequence[t] = current_state % jj;
	current_state = path[t][current_state];
      }
    }
    assert(current_state == -1);
  }
  return_value = prior + return_value - track_score;
  /* Clean up */
  for (t = 0; t < tt; t ++){
    free(alpha[t]);
    free(outL[t]);
    free(path[t]);
  }
  free(us);
  free(hmms);
  free(alpha);
  free(outL);
  free(path);
  free(js);
  free(ks);
  return return_value;
}

Real sentence_maximum_one
(Sentence s, int nTracks, IVec *roles, BoxesMovie bm, Flow **flow_movie, int fl_tt, 
 Real scale, char **objects, int ol, FeatureMedoid **fms, int mn, 
 enum model_constraint model_constraint, int final_state, int *box_sequence){
  
  Real prior, maximum;
  IVec eligible_states, *secondary_eligible_states;
  xHmm *xhmm;
  Hmm **hmms;
  int w, j, tt;
  tt = bm->tt;

  hmms = safe_malloc(s->ww * sizeof *hmms);
  for (w = 0; w < s->ww; w ++)
    hmms[w] = s->ws[w]->hmm;
  xhmm = initializeXHMM(hmms, s->ww, bm, flow_movie, fl_tt, scale);

  compute_eligible_states(&s, &nTracks, 1, &roles, bm, model_constraint,
			  &eligible_states, &secondary_eligible_states);
  compute_sentence_priors(&s, 1, tt, &prior);

  maximum = viterbi_sentence_tracker(s, xhmm, nTracks, roles, bm, flow_movie, fl_tt, scale,
				     prior, objects, ol, fms, mn, eligible_states,
				     secondary_eligible_states, 1, final_state, box_sequence);
  /* Clean up */
  for (j = 0; j < VLENGTH(eligible_states); j ++)
    freeIVec(secondary_eligible_states[j]);
  freeIVec(eligible_states);
  free(secondary_eligible_states);
  free(hmms);
  freeXHMM(xhmm);
  return maximum;
}

Real *sentence_likelihoods_one_video
(Sentence *ss, int nn, int *nTracks, IVec **roles, BoxesMovie bm, Flow **flow_movie, 
 int fl_tt, Real scale, char **objects, int ol, FeatureMedoid **fms, int mn, 
 enum model_constraint model_constraint, int final_state){

  Real *priors, *likelihoods;
  IVec *eligible_states, **secondary_eligible_states;
  xHmm **xhmm;
  Hmm **hmms;
  int j, tt, n, w;
  tt = bm->tt;

  likelihoods = safe_malloc(nn * sizeof *likelihoods);
  eligible_states = safe_malloc(nn * sizeof *eligible_states);
  secondary_eligible_states = safe_malloc(nn * sizeof *secondary_eligible_states);
  priors = safe_malloc(nn * sizeof *priors);
  xhmm = safe_malloc(nn * sizeof *xhmm);

  compute_eligible_states(ss, nTracks, nn, roles, bm, model_constraint,
  			  eligible_states, secondary_eligible_states);
  compute_sentence_priors(ss, nn, tt, priors);

  for (n = 0; n < nn; n ++){
    hmms = safe_malloc(ss[n]->ww * sizeof *hmms);
    for (w = 0; w < ss[n]->ww; w ++)
      hmms[w] = ss[n]->ws[w]->hmm;
    xhmm[n] = initializeXHMM(hmms, ss[n]->ww, bm, flow_movie, fl_tt, scale);
    free(hmms);
  }

#pragma omp parallel for private(n)
  for (n = 0; n < nn; n ++)
    likelihoods[n] = viterbi_sentence_tracker(ss[n], xhmm[n], nTracks[n], roles[n], bm, flow_movie, fl_tt,
  					      scale, priors[n], objects, ol, fms, mn, eligible_states[n],
  					      secondary_eligible_states[n], 0, final_state, NULL);

  /* Clean up */
  for (n = 0; n < nn; n ++){
    for (j = 0; j < VLENGTH(eligible_states[n]); j ++)
      freeIVec(secondary_eligible_states[n][j]);
    freeIVec(eligible_states[n]);
    free(secondary_eligible_states[n]);
    freeXHMM(xhmm[n]);
  }
  free(xhmm);
  free(eligible_states);
  free(secondary_eligible_states);
  free(priors);
  return likelihoods;
}

UpdateResults *updateX(Sentence *competition_set, Sentence *scratch_models,
		       int *nTracks, int nSentences, IVec **roles_multiple, BoxesMovie bm,
		       Flow **flow_movie, int fl_tt, Real scale, Real log_D,
		       enum training_mode training_mode,
		       char **objects, int ol, FeatureMedoid **fms, int mn,
		       enum model_constraint model_constraint){
  
  Real *posteriors = safe_malloc(nSentences * sizeof *posteriors);
  Real *priors = safe_malloc(nSentences * sizeof *priors);
  Sentence *cs = competition_set;
  Sentence *sm = scratch_models;
  IVec *eligible_states, **secondary_eligible_states;
  Real ***xGamma, ***xAlpha, ***xBeta, ***xState_like, *like, total_post;
  xHmm **xhmms;
  Hmm **hmms;
  UpdateResults *res = safe_malloc(sizeof *res);
  int w, j, t, tt, n, uu, jj;
  tt = bm->tt;

  /* Allocate and initialize an xHmm for each sentence in the competition set */
  xhmms = safe_malloc(nSentences * sizeof *xhmms);
  for (n = 0; n < nSentences; n ++){
    hmms = safe_malloc(cs[n]->ww * sizeof *hmms);
    for (w = 0; w < cs[n]->ww; w ++)
      hmms[w] = cs[n]->ws[w]->hmm;
    xhmms[n] = initializeXHMM(hmms, cs[n]->ww, bm, flow_movie, fl_tt, scale);
    free(hmms);
  }
  /* Compute the eligible states for each sentence
     The computation is defined by the model assignment constraint
   */
  eligible_states = safe_malloc(nSentences * sizeof *eligible_states);
  secondary_eligible_states = safe_malloc(nSentences * sizeof *secondary_eligible_states);
  compute_eligible_states(cs, nTracks, nSentences, roles_multiple, bm, model_constraint,
			  eligible_states, secondary_eligible_states);
  /* Allocate space for gamma, alpha, beta, state likelihood, and sentence likelihood,
     which will be used for estimation.
   */
  xGamma = safe_malloc(nSentences * sizeof *xGamma);
  xAlpha = safe_malloc(nSentences * sizeof *xAlpha);
  xBeta = safe_malloc(nSentences * sizeof *xBeta);
  xState_like = safe_malloc(nSentences * sizeof *xState_like);
  for (n = 0; n < nSentences; n ++){
    /* For now, only two or three tracks are supported */
    assert(nTracks[n] == 2 || nTracks[n] == 3);
    xGamma[n] = safe_malloc(tt * sizeof *(xGamma[n]));
    xAlpha[n] = safe_malloc(tt * sizeof *(xAlpha[n]));
    xBeta[n] = safe_malloc(tt * sizeof *(xBeta[n]));
    xState_like[n] = safe_malloc(tt * sizeof *(xState_like[n]));
    jj = VLENGTH(eligible_states[n]);
    uu = jj * xhmms[n]->m->uu;
    for (t = 0; t < tt; t ++){
      xGamma[n][t] = safe_malloc(uu * sizeof *(xGamma[n][t]));
      xAlpha[n][t] = safe_malloc(uu * sizeof *(xAlpha[n][t]));
      xBeta[n][t] = safe_malloc(uu * sizeof *(xBeta[n][t]));
      xState_like[n][t] = safe_malloc(uu * sizeof *(xState_like[n][t]));
    }
  }
  like = safe_malloc(nSentences * sizeof *like);
  /* Do some preparation works for estimation
     Including priors, gamma, alpha, beta, and posteriors
   */
  compute_sentence_priors(cs, nSentences, tt, priors);

  compute_sentence_statistics(cs, xhmms, nTracks, nSentences, roles_multiple, bm, flow_movie, fl_tt, scale, 
			      priors, objects, ol, fms, mn, eligible_states, secondary_eligible_states,
			      xGamma, xAlpha, xBeta, xState_like, like, posteriors, &total_post);

  /* Update the models sentence by sentence */
  for (n = 0; n < nSentences; n ++){
    HMM_updateXModel(cs[n], sm[n], xhmms[n], eligible_states[n], secondary_eligible_states[n],
		     nTracks[n], xAlpha[n], xBeta[n], xState_like[n], xGamma[n][0], like[n],
		     my_exp(posteriors[n]), log_D, training_mode, (n == 0? 1: 0));
    FFM_updateXModel(cs[n], sm[n], roles_multiple[n], xGamma[n], xhmms[n]->m->uu,
		     eligible_states[n], nTracks[n], bm, flow_movie, scale,
		     my_exp(posteriors[n]), log_D, training_mode, (n == 0? 1: 0),
		     objects, ol, fms, mn);
  }
  /* Return both dt and ml objective function values */
  res->discrimination = posteriors[0];
  res->likelihood = posteriors[0] + total_post;

  /* Clean up */
  for (n = 0; n < nSentences; n ++){
    freeXHMM(xhmms[n]);
    jj = VLENGTH(eligible_states[n]);
    for (j = 0; j < jj; j ++)
      freeIVec(secondary_eligible_states[n][j]);
    free(secondary_eligible_states[n]);
    freeIVec(eligible_states[n]);
    for (t = 0; t < tt; t ++){
      free(xGamma[n][t]);
      free(xAlpha[n][t]);
      free(xBeta[n][t]);
      free(xState_like[n][t]);
    }
    free(xGamma[n]);
    free(xAlpha[n]);
    free(xBeta[n]);
    free(xState_like[n]);
  }
  free(xhmms);
  free(eligible_states);
  free(secondary_eligible_states);
  free(xGamma);
  free(xAlpha);
  free(xBeta);
  free(xState_like);
  free(like);
  free(priors);
  free(posteriors);
  return res;
}

int update_with_box_scores(Model **m, Hmm **tmp_hmm, RMat *data, Real **postpC, Real log_D,
	   int *c_ls, int ll, int cc, enum training_mode training_mode,
			   int upper_triangular, RMat *score_matrices) {
  /* tmp_hmm[c] is scratch space. It must have at least as many states as
     m[c]->hmm. */
  int c, l, u;
  int *xu;
  Real** scores;
  for (c = 0; c<cc; c++) {
    if (g_lastData!=data||ll>g_ll||m[c]->uu>g_uu) {
      g_lastData = data;
      initGlobals(ll, m[c]->uu, data);
    }
    zeroHMMlinear(tmp_hmm[c]);
    for (l = 0; l<ll; l++) {
      scores = MATRIX(score_matrices[l]);
      switch (training_mode) {
      case HMM_ML:
	if (c!=c_ls[l]) continue;
	for (u = 0; u<m[c]->uu; u++) {
	  FFM_logL_with_box_scores(m[c]->ffm[u], g_logL[l][u], data[l],scores[u]);
	}
	HMM_updateModel(m[c]->hmm, tmp_hmm[c], g_logL[l], g_gamma[l], log_D,
			0.0, -1, -1, training_mode);
	break;
      case HMM_DT:
	for (u = 0; u<m[c]->uu; u++) {
	  FFM_logL_with_box_scores(m[c]->ffm[u], g_logL[l][u], data[l],scores[u]);
	}
	HMM_updateModel(m[c]->hmm, tmp_hmm[c], g_logL[l], g_gamma[l], log_D,
			my_exp(postpC[c][l]), c, c_ls[l], training_mode);
	break;
      default: panic("unrecognized training mode");
      }
    }

    xu = safe_malloc(sizeof *xu * m[c]->uu);
    if (!normaliseHMMlinear(tmp_hmm[c], upper_triangular, training_mode, xu)) {
      assert(training_mode == HMM_DT);
      free(xu);
      return FALSE;
    }

    copyHMM(m[c]->hmm, tmp_hmm[c]);
    for (u = 0; u<m[c]->uu; u++) {
      switch (training_mode) {
      case HMM_ML:
	assert(FFM_maximise(m[c]->ffm[u], data, g_gamma_r[u], ll, log_D,
			    NULL, c, c_ls));
	break;
      case HMM_DT:
	if (!FFM_maximise(m[c]->ffm[u], data, g_gamma_r[u], ll, log_D,
			  postpC[c], c, c_ls))
	  return FALSE;
	break;
      default: panic("unrecognized training mode");
      }
    }
  }
  return TRUE;
}

void removeStates(Model *m, int *xu, Hmm *hmm)
{
  int v, u, w, old_uu = m->uu;
  Hmm *phmm = m->hmm;
  for(u = 0; u < m->uu; ){
    if(xu[u]){
      if(u < m->uu - 1){
	for(v = u; v < m->uu - 1; v ++){
	  xu[v] = xu[v+1];
	  copyFFM(m->ffm[v], m->ffm[v+1]);
	  set_model_b(m, v, my_exp(VECTOR(phmm->logB)[v+1]));
	  copyRVec(MATRIX(phmm->logA)[v], MATRIX(phmm->logA)[v+1], m->uu);
	  for(w = 0; w < m->uu; w ++)
	    set_model_a(m, w, v, my_exp(MATRIX(phmm->logA)[w][v+1]));
	  if (hmm){
	    VECTOR(hmm->logB)[v] = VECTOR(hmm->logB)[v+1];
	    copyRVec(MATRIX(hmm->logA)[v], MATRIX(hmm->logA)[v+1], m->uu);
	    for(w = 0; w < m->uu; w ++)
	      MATRIX(hmm->logA)[w][v] = MATRIX(hmm->logA)[w][v+1];
	  }
	}
      }
      m->uu --;
      phmm->uu --;
      if (hmm) hmm->uu --;
    }
    else u ++;
  }
  assert(m->uu > 0);
  /* Clean up the redundant memory. DO NOT clean up hmm matrix now. It will be released
     according to the size of RMat instead of hmm->uu. */
  for(u = m->uu; u < old_uu; u ++)
    freeFFM(m->ffm[u]);
}

/* -- accessor functions -- */
int model_ii(Model *m)
{ assert(m!=NULL&&m->uu>0);
  return m->ffm[0]->ii;}

int model_nn(Model *m, int i){
  assert(m != NULL);
  if (i < 0 || i >= model_ii(m))
    panic("model_nn: i = %d out of range [0, %d)", i, model_ii(m));
  assert(m->ffm[0]->ft[i] == FT_DISCRETE);
  return ((DParam *)m->ffm[0]->p[i])->kk;
}

int model_feature_type(Model *m, int i)
{ assert(m!=NULL&&m->uu>0);
  if (i<0||i>=model_ii(m))
  { panic("model_feature_type(): i = %d out of range [0, %d)",
	  i, model_ii(m));}
  return m->ffm[0]->ft[i];}

Real model_parameter(Model *m, int u, int i, int n)
{ assert(m!=NULL&&m->uu>0);
  if (i<0||i>=model_ii(m))
  { panic("model_parameter(): i = %d out of range [0, %d]", i, model_ii(m));}
  if (u<0||u>=m->uu)
  { panic("model_parameter(): u = %d out of range [0, %d]", u, m->uu);}
  switch (m->ffm[u]->ft[i])
  { case FT_CONTINUOUS:
    switch (n)
    { case 0: return ((Param *)m->ffm[u]->p[i])->mu;
      case 1: return ((Param *)m->ffm[u]->p[i])->sigma;
      default: panic("model_parameter(): n = %d out of range [0, 1]", n);}
    case FT_RADIAL:
    switch (n)
    { case 0: return ((VMParam *)m->ffm[u]->p[i])->mean;
      case 1: return ((VMParam *)m->ffm[u]->p[i])->kappa;
      default: panic("model_parameter(): n = %d out of range [0, 1]", n);}
    case FT_DISCRETE:
    switch (n)
    { case 0: return ((DParam *)m->ffm[u]->p[i])->kk;
      default:
	if (n < 1 || n > ((DParam *)m->ffm[u]->p[i])->kk)
	  panic("model_parameter(): n = %d out of range [0, %d)", n,
		((DParam *)m->ffm[u]->p[i])->kk);
      return ((DParam *)m->ffm[u]->p[i])->p[n-1];}
    default:
    panic("model_parameter(): Unrecognised feature type: %d",
	  m->ffm[u]->ft[i]);}
  panic("model_parameter(): Control shouldn't reach this point");
  return 0.0;}

int model_uu(Model *m)
{ assert(m!=NULL);
  return m->uu;}

Hmm *model_hmm(Model *m){
  assert(m!=NULL);
  return m->hmm;
}

Ffm *model_ffm(Model *m, int i){
  assert(m!=NULL);
  return m->ffm[i];
}

Real model_a(Model *m, int u, int v)
{ assert(m!=NULL&&m->hmm!=NULL);
  if (u<0||u>=m->uu||v<0||v>=m->uu)
  { panic("model_a(): u = %d or v = %d out of range [0, %d]", u, v, m->uu);}
  return my_exp(MATRIX(m->hmm->logA)[u][v]);}

Real model_b(Model *m, int u)
{ assert(m!=NULL&&m->hmm!=NULL);
  if (u<0||u>=m->uu) panic("model_b(): u = %d out of range [0, %d]", u, m->uu);
  return my_exp(VECTOR(m->hmm->logB)[u]);}

char *model_name(Model *m)
{ assert(m != NULL);
  return m->name;
}

int model_n_roles(Model *m)
{ assert(m != NULL && m->n_roles > 0);
  return m->n_roles;
}

enum part_of_speech model_pos(Model *m)
{ assert(m != NULL);
  return m->pos;
}

void set_model_parameter(Model *m, int u, int i, int n, Real x)
{ int j;
  assert(m!=NULL&&m->uu>0);
  if (i<0||i>=model_ii(m))
  { panic("set_model_parameter(): i = %d out of range [0, %d]",
	  i, model_ii(m));}
  if (u<0||u>=m->uu)
  { panic("set_model_parameter(): u = %d out of range [0, %d]", u, m->uu);}
  if (m->ffm[u]->p[i]==NULL)
  { panic("set_model_parameter(): feature %d is not initialised", i);}
  switch (m->ffm[u]->ft[i])
  { case FT_CONTINUOUS:
    switch (n)
    { case 0:
      ((Param *)m->ffm[u]->p[i])->mu = x;
      break;
      case 1:
      if (x<=0.0){
	((Param *)m->ffm[u]->p[i])->sigma = 0.01;
      } 
      else { ((Param *)m->ffm[u]->p[i])->sigma = x; }
      break;
      default:
      panic("set_model_parameter(): n = %d out of range [0, 1]", n);}
    break;
    case FT_RADIAL:
    switch (n)
    { case 0:
      ((VMParam *)m->ffm[u]->p[i])->mean = x;
      break;
      case 1:
      if (x<=0.0){
	((VMParam *)m->ffm[u]->p[i])->kappa = 0.01;
      } else { ((VMParam *)m->ffm[u]->p[i])->kappa = x; }
      break;
      default:
      panic("set_model_parameter(): n = %d out of range [0, 1]", n);}
    break;
    case FT_DISCRETE:
    switch (n)
    { case 0:
      j = (int)round(x);
      if (j<=0||j>MAX_DISCRETE)
	panic("set_model_parameter(): x = %f is out of range [1, MAX_DISCRETE]", x);
      ((DParam *)m->ffm[u]->p[i])->kk = j;
      break;
      default:
      if (n-1<0||n-1>=((DParam *)m->ffm[u]->p[i])->kk)
	panic("model_parameter(): n-1 = %d out of range [0, kk - 1]", n-1);
      ((DParam *)m->ffm[u]->p[i])->p[n-1] = x; }
    break;
    default:
    panic("set_model_paramter(): Unrecognised feature type: %d, %d, %d",
	  m->ffm[u]->ft[i], u, i);}}

void set_model_a(Model *m, int u, int v, Real x)
{ assert(m!=NULL&&m->hmm!=NULL);
  if (u<0||u>=m->uu||v<0||v>=m->uu)
  { panic("set_model_a(): u = %d or v = %d out of range [0, %d]",
	  u, v, m->uu);}
  if (x<0.0||x>1.0) panic("set_model_a(): x = %f is out of range [0, 1]", x);
  MATRIX(m->hmm->logA)[u][v] = my_log(x);}

void set_model_a_linear(Model *m, int u, int v, Real x)
{ assert(m!=NULL&&m->hmm!=NULL);
  if (u<0||u>=m->uu||v<0||v>=m->uu)
  { panic("set_model_a(): u = %d or v = %d out of range [0, %d]",
	  u, v, m->uu);}
  MATRIX(m->hmm->logA)[u][v] = x;}

void set_model_b(Model *m, int u, Real x)
{ assert(m!=NULL&&m->hmm!=NULL);
  if (u<0||u>=m->uu)
  { panic("set_model_b(): u = %d out of range [0, %d]", u, m->uu);}
  if (x<0.0||x>1.0) panic("set_model_b(): x = %f is out of range [0, 1]", x);
  VECTOR(m->hmm->logB)[u] = my_log(x);}

void set_model_b_linear(Model *m, int u, Real x)
{ assert(m!=NULL&&m->hmm!=NULL);
  if (u<0||u>=m->uu)
  { panic("set_model_b(): u = %d out of range [0, %d]", u, m->uu);}
  VECTOR(m->hmm->logB)[u] = x;}

void set_model_name(Model *m, char *name)
{ assert(m != NULL && name != NULL);
  if (m->name) free(m->name);
  m->name = safe_malloc(strlen(name) + 1);
  strcpy(m->name, name);
}

void set_model_n_roles(Model *m, int n)
{ assert(m != NULL);
  if (n < 0)
    panic("set_model_n_roles: n = %d is negative", n);
  m->n_roles = n;
}

void set_model_pos(Model *m, enum part_of_speech pos)
{ assert(m != NULL);
  m->pos = pos;
}

char *model_pos_str(enum part_of_speech pos)
{
  switch(pos){
  case NOUN: return "noun";
  case VERB: return "verb";
  case ADVERB: return "adverb";
  case ADJECTIVE: return "adjective";
  case PREPOSITION: return "preposition";
  case MOTION_PREPOSITION: return "motion_preposition";
  default: ;
  }
  return "other";
}

/* Tam V'Nishlam Shevah L'El Borei Olam */
