/* This file is just a redeclaration of Real as adouble for the use of ADOLC */

#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <string.h>

#ifdef _OPENMP
#include <omp.h>
#include <adolc/adolc_openmp.h>
#endif

extern "C" {
#include "hmm.h"
#include "hmm-control.h"
}

#include "hmm-likelihood-misc-AD.h"

ADOLC_TAPELESS_UNIQUE_INTERNALS;

#define U(u) VECTOR(es)[u]
#define J(u) VECTOR(es2[j])[u]

extern "C" void panic(const char *error_text, ...);

adtl::adouble my_max(adtl::adouble x, adtl::adouble y){
  if (x > y) return x;
  else return y;
}

adtl::adouble my_min(adtl::adouble x, adtl::adouble y){
  if (x < y) return x;
  else return y;
}

adtl::adouble my_add_exp(adtl::adouble e1, adtl::adouble e2){ 
  adtl::adouble e_max = fmax(e1, e2), e_min = fmin(e1, e2);
  return (e_max == NEGATIVE_INFINITY)? log(exp(e_max) + exp(e_min)):
    log(1.0 + exp(e_min - e_max)) + e_max;
}

adDParam *allocAdDParam(int kk){
  adDParam *p = new adDParam;
  p->kk = kk;
  p->p = new adtl::adouble[kk];
  return p;
}

void freeAdDParam(adDParam *p){
  if (p){
    delete [] p->p;
    delete p;
  }
}

adRVec allocAdRVec(size_t x)
{ adRVec v = new adRVecStruct;
  VECTOR(v) = new adtl::adouble[x];
  VLENGTH(v) = x;
  return v;}

adRMat allocAdRMat(size_t y, size_t x)
{ size_t i;
  adRMat m = new adRMatStruct;
  MATRIX(m) = new adtl::adouble*[y];
  for (i = 0; i < y; i++) 
    MATRIX(m)[i] = new adtl::adouble[x];
  ROWS(m) = y;
  COLUMNS(m) = x;
  return m;}

void freeAdRVec(adRVec v)
{ if (v){ 
    if (VECTOR(v)) 
      delete [] VECTOR(v);
    delete v;
  }
}

void freeAdRMat(adRMat m)
{ size_t i;
  if (m)
  { if (MATRIX(m))
    { for (i = 0; i < ROWS(m); i++)
      { if (MATRIX(m)[i]) 
	  delete [] MATRIX(m)[i];}
      delete [] MATRIX(m);}
    delete m;}}

adFfm *allocAdFFM(int ii)
{ adFfm *m = new adFfm;
  m->p = new adDParam*[ii];
  m->lf = new adLogLikeF[ii];
  m->ii = ii;
  for (ii--; ii>=0; ii--){ 
    m->lf[ii] = adLogDiscrete;
    m->p[ii] = NULL;
  }
  return m;}

void freeAdFFM(adFfm *m)
{ int ii = m->ii;
  for (ii--; ii >= 0; ii--)
    if (m->p[ii]) freeAdDParam(m->p[ii]);
  delete [] m->lf;
  delete [] m->p;
  delete m;}

adModel *allocAdModel(int ii, int uu)
{ int u;
  if (ii <= 0 || uu <= 0) 
    panic("allocAdModel(): %s must be >0", ii <=0 ? "ii": "uu");
  adModel *m = new adModel;
  m->uu = uu;
  m->hmm = allocAdHMM(uu);
  m->ffm = new adFfm*[uu];
  for (u = 0; u < uu; u++) 
    m->ffm[u] = allocAdFFM(ii);
  m->n_roles = 0;
  m->name = NULL;
  m->pos = OTHER;
  return m;}

void freeAdModel(adModel *m)
{ int u;
  freeAdHMM(m->hmm);
  for (u = 0; u < m->uu; u++) freeAdFFM(m->ffm[u]);
  delete [] m->ffm;
  if (m->name) delete [] m->name;
  delete m;}

void ad_set_model_name(adModel *m, char *name)
{ assert(m != NULL && name != NULL);
  if (m->name) delete [] m->name;
  m->name = new char[strlen(name) + 1];
  strcpy(m->name, name);
}

adtl::adouble adLogDiscrete(Real x, adDParam *p)
{ int i = (int)round(x);
  assert(i >= 0 && i < p->kk);
  return log(p->p[i]);}

adtl::adouble adFFM_logL_one(adFfm *m, RVec data){
  int i, ii = m->ii;
  adDParam **p = m->p;
  adtl::adouble l = 0.0;
  Real *x = VECTOR(data);
  assert(VLENGTH(data) == ii);
  for (i = 0; i < ii; i ++)
    l += (m->lf[i])(x[i], p[i]);
  return l;
}

void ad_setFeatKK(adFfm *m, int i, int kk)
{ assert(i >= 0 && i < m->ii);
  m->p[i] = allocAdDParam(kk);
  return;
}

adHmm *allocAdHMM(int uu)
{ assert(uu>0);
  adHmm *m = new adHmm;
  m->logA = allocAdRMat(uu, uu);
  m->logB = allocAdRVec(uu);
  m->uu = uu;
  return m;}

void freeAdHMM(adHmm *m)
{ freeAdRMat(m->logA);
  freeAdRVec(m->logB);
  delete m;}

adXHmm *allocAdXHMM(int uu, int ii, int tt){
  assert(uu > 0 && ii > 0 && tt > 0);
  adXHmm *xhmm = new adXHmm;
  xhmm->m = allocAdHMM(uu);
  xhmm->ii = ii;
  xhmm->tt = tt;
  if (tt > 1)
    xhmm->bps = allocRMat3d(tt - 1, ii, ii);
  else xhmm->bps = NULL;
  xhmm->bss = allocRMat(tt, ii);
  return xhmm;
}

void freeAdXHMM(adXHmm *xhmm){
  if (xhmm){
    freeAdHMM(xhmm->m);
    freeRMat(xhmm->bss);
    if (xhmm->bps) freeRMat3d(xhmm->bps);
    delete xhmm;
  }
}

int adHMM_xStateRange(adHmm **hmms, int ww)
{ int w, tot = 1;
  for(w = 0; w < ww; w ++)
    tot *= hmms[w]->uu;
  return tot;
}

void adHMM_decodeXStates(int *us, adHmm **hmms, int u, int ww)
{ int w;
  for(w = ww - 1; w >= 0; w --){
    us[w] = u % hmms[w]->uu;
    u /= hmms[w]->uu;
  }
}

adtl::adouble adHMM_xB(adHmm **hmms, int u, int ww)
{ 
  int *us = new int[ww];
  int w;
  adtl::adouble tot = 0;
  adHMM_decodeXStates(us, hmms, u, ww);
  for(w = 0; w < ww; w ++)
    tot += VECTOR(hmms[w]->logB)[us[w]];
  delete [] us;
  return tot;
}

adtl::adouble adHMM_xA(adHmm **hmms, int u, int v, int ww)
{
  int *us = new int[ww];
  int *vs = new int[ww];
  int w;
  adtl::adouble tot = 0;
  adHMM_decodeXStates(us, hmms, u, ww);
  adHMM_decodeXStates(vs, hmms, v, ww);
  for(w = 0; w < ww; w ++)
    tot += MATRIX(hmms[w]->logA)[us[w]][vs[w]];
  delete [] us;
  delete [] vs;
  return tot;
}

adXHmm *initializeAdXHMM(adHmm **hmms, int ww, BoxesMovie bm, Flow **flow_movie, 
			 int fl_tt, Real scale){
  int t, tt, i, j, ii, uu, u, v;
  Real ***bps, **bss;
  adtl::adouble **a, *b;
  Box ***ds;
  adXHmm *xhmm;
  assert(bm->tt == fl_tt);
  tt = bm->tt;
  ii = bm->ii;

  uu = adHMM_xStateRange(hmms, ww);
  xhmm = allocAdXHMM(uu, ii, tt);
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
    b[u] = adHMM_xB(hmms, u, ww);
    for (v = 0; v < uu; v ++)
      a[u][v] = adHMM_xA(hmms, u, v, ww);
  }
  return xhmm;
}


void ad_compute_eligible_states
(adSentence *ss, int *nTracks, int nn, IVec **roles_multiple, BoxesMovie bm,
 enum model_constraint model_constraint, IVec *eligible_states, 
 IVec **secondary_eligible_states){
 
  int *js, nRoles, w, *r, *eligible, n, jjj, u, i, ii;
  int eligible_tot, tt, t, cnt, *ks, jj, j, k;
  Box *d1, *d2, **ds;
  IVec es, *es2, *roles;
  ii = bm->ii;
  tt = bm->tt;
  /* make sure the order of box models in each frame is coherent */
  ds = bm->ds[0];
  for (t = 1; t < tt; t ++)
    for (i = 0; i < ii; i ++)
      assert(box_similarity((bm->ds[t])[i], ds[i]) == 0);

  for (n = 0; n < nn; n ++){
    jjj = 1;
    for (i = 0; i < nTracks[n]; i ++) jjj *= ii;
    js = new int[nTracks[n]];
    eligible = new int[jjj];
    roles = roles_multiple[n];
    eligible_tot = 0;
    for (u = 0; u < jjj; u ++){
      eligible[u] = 1;
      if (model_constraint == NO_DUPLICATE_MODELS){
	decode_box_states(u, ii, nTracks[n], js);
	for (w = 0; w < ss[n]->ww; w ++){
	  assert(ss[n]->ws[w]->n_roles == VLENGTH(roles[w]));
	  nRoles = VLENGTH(roles[w]);
	  assert(nRoles == 1 || nRoles == 2);  /* Not apply to nRoles >= 3 */
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
    delete [] eligible;
    /* Now for each box state, compute the previously eligible box states such
       that the box similarity score between each eligible state and the
       current state is not -inf
    */
    jj = VLENGTH(es);
    ks = new int[nTracks[n]];
    secondary_eligible_states[n] = new IVec[jj];
    es2 = secondary_eligible_states[n];
    eligible = new int[jj];
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
    delete [] eligible;
    delete [] ks;
    delete [] js;
  }
}

void ad_compute_sentence_priors(adSentence *ss, int nn, int tt, Real *priors){
  int n, w, i, uu;
  adFfm *ffm;
  adDParam *p;
  /* Compute the prior for each sentence */
  for (n = 0; n < nn; n ++){
    priors[n] = 0;  /* Log space to */
    for (w = 0; w < ss[n]->ww; w ++){
      ffm = ss[n]->ws[w]->ffm[0];
      for (i = 0; i < ffm->ii; i ++){
    	p = ffm->p[i];
    	priors[n] += tt * log(p->kk);
      }
      /* Add a weight for the prior */
      uu = ss[n]->ws[w]->uu;
      priors[n] += tt * log(uu);
    }
  }
}

void ad_compute_crossed_likelihood
(IVec es, int vv, adSentence s, int nTracks, IVec *roles, BoxesMovie bm, 
 Flow **flow_movie, Real scale, char **objects, int ol, FeatureMedoid **fms, int mn,
 adtl::adouble **outL){

#define OUTPUT_MODEL(s, w, u)			\
  s->ws[w]->ffm[u]

  int *js, *us, nRoles, w, *r, t, tt, ii, j, u, jj, x;
  adHmm **hmms;
  Box *d1, *d2, *d3, **ds;
  RVec *data;
  Features f;
  hmms = new adHmm*[s->ww];
  us = new int[s->ww];
  js = new int[nTracks];
  data = new RVec[s->ww];
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
	assert(nRoles <= 3 && nRoles >= 1);  /* Each model has no more than three roles */
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
	adHMM_decodeXStates(us, hmms, u, s->ww);
	x = u * jj + j;
	outL[t][x] = 0;
	for (w = 0; w < s->ww; w ++)
	  outL[t][x] += adFFM_logL_one(OUTPUT_MODEL(s, w, us[w]), data[w]);
      }
      for (w = 0; w < s->ww; w ++)
	freeRVec(data[w]);
    }
  }
  delete [] js;
  delete [] hmms;
  delete [] us;
  delete [] data;
  return;
}

adtl::adouble ad_viterbi_sentence_tracker
(adSentence s, adXHmm *xhmm, int nTracks, IVec *roles, BoxesMovie bm, Flow **flow_movie,
 int fl_tt, Real scale, Real prior, char **objects, int ol, FeatureMedoid **fms,
 int mn, IVec es, IVec *es2, int *box_sequence){
  
  int t, tt, j, jj, k, kk, u, vv, uu, v, w, x, y, *js, *ks, ii, i, *us, idx = 0;
  adtl::adouble **outL, **a, *b,  **alpha, return_value, transition_score;
  Real ***bps, **bss, track_score, single, pair;
  adHmm **hmms;
  tt = bm->tt;
  ii = bm->ii;
  assert(tt = fl_tt);
  jj = VLENGTH(es);
  vv = xhmm->m->uu;
  uu = jj * vv;
  alpha = new adtl::adouble*[uu];
  outL = new adtl::adouble*[tt];
  for (t = 0; t < tt; t ++)
    outL[t] = new adtl::adouble[uu];

  a = MATRIX(xhmm->m->logA);
  b = VECTOR(xhmm->m->logB);
  bps = MATRIX(xhmm->bps);
  bss = MATRIX(xhmm->bss);

  ad_compute_crossed_likelihood(es, vv, s, nTracks, roles, bm, flow_movie, scale,
				objects, ol, fms, mn, outL);

  track_score = viterbi_tracker_score(bps, bss, nTracks, ii, tt, es, es2);
  /* Start tracking */
  js = new int[nTracks];
  ks = new int[nTracks];
  /* t = 0 */
  for (j = 0; j < jj; j ++){
    decode_box_states(U(j), ii, nTracks, js);
    single = 0;
    /* Box single score at frame 0 */
    for (i = 0; i < nTracks; i ++)
      single += bss[0][js[i]];
    for (u = 0; u < vv; u ++){
      x = u * jj + j;
      alpha[x] = new adtl::adouble[2];
      /* Initial state probability times output probability */
      alpha[x][0] = b[u] + outL[0][x] + single;
    }
  }
  /* t >= 1 */
  for (t = 1; t < tt; t ++){
    idx = t % 2;
    for (j = 0; j < jj; j ++){
      decode_box_states(U(j), ii, nTracks, js);
      single = 0;
      for (i = 0; i < nTracks; i ++)
	single += bss[t][js[i]];
      kk = VLENGTH(es2[j]);
      for (u = 0; u < vv; u ++){
	x = u * jj + j;
	alpha[x][idx] = NEGATIVE_INFINITY;
	for (k = 0; k < kk; k ++){
	  decode_box_states(U(J(k)), ii, nTracks, ks);
	  pair = 0;
	  for (i = 0; i < nTracks; i ++)
	    pair += bps[t-1][ks[i]][js[i]];
	  assert(pair != NEGATIVE_INFINITY);
	  for (v = 0; v < vv; v ++){
	    y = v * jj + J(k);
	    transition_score = alpha[y][1-idx] + pair + a[v][u];
	    alpha[x][idx] = my_add_exp(alpha[x][idx], transition_score);
	  }
	}
	alpha[x][idx] += single + outL[t][x];
      }
    }
  }
  us = new int[s->ww];
  hmms = new adHmm*[s->ww];
  for (w = 0; w < s->ww; w ++)
    hmms[w] = s->ws[w]->hmm;

  return_value = NEGATIVE_INFINITY;

  for (u = 0; u < uu; u ++)
    return_value = my_add_exp(return_value, alpha[u][idx]);
  
  return_value = prior + return_value - track_score;
  /* Clean up */
  for (u = 0; u < uu; u ++)
    delete [] alpha[u];
  for (t = 0; t < tt; t ++)
    delete [] outL[t];
  delete [] alpha;
  delete [] us;
  delete [] hmms;
  delete [] outL;
  delete [] js;
  delete [] ks;
  return return_value;
}

adtl::adouble ad_sentence_likelihood_one
(adSentence s, int nTracks, IVec *roles, BoxesMovie bm, Flow **flow_movie, int fl_tt, 
 Real scale, char **objects, int ol, FeatureMedoid **fms, int mn, 
 enum model_constraint model_constraint){
 
  Real prior;
  adtl::adouble likelihood;
  IVec eligible_states, *secondary_eligible_states;
  adXHmm *xhmm;
  adHmm **hmms;
  int w, j, tt;  
  
  tt = bm->tt;
  hmms = new adHmm *[s->ww];
  for (w = 0; w < s->ww; w ++)
    hmms[w] = s->ws[w]->hmm;
  xhmm = initializeAdXHMM(hmms, s->ww, bm, flow_movie, fl_tt, scale);

  ad_compute_eligible_states(&s, &nTracks, 1, &roles, bm, model_constraint,
			     &eligible_states, &secondary_eligible_states);
  ad_compute_sentence_priors(&s, 1, tt, &prior);

  likelihood = ad_viterbi_sentence_tracker(s, xhmm, nTracks, roles, bm, flow_movie, fl_tt, scale,
					   prior, objects, ol, fms, mn, eligible_states,
					   secondary_eligible_states, NULL);
  /* Clean up */
  for (j = 0; j < VLENGTH(eligible_states); j ++)
    freeIVec(secondary_eligible_states[j]);
  freeIVec(eligible_states);
  delete [] secondary_eligible_states;
  delete [] hmms;
  freeAdXHMM(xhmm);
  return likelihood / tt;
}

extern "C" PackInfo *allocPackInfo(int n_models){
  assert(n_models > 0);
  int j;
  PackInfo *stats = new PackInfo;
  stats->n_models = n_models;
  stats->uu = new int[n_models];
  stats->ii = new int[n_models];
  stats->nn = new int*[n_models];
  for (j = 0; j < n_models; j ++)
    stats->nn[j] = NULL;
  return stats;
}

extern "C" void freePackInfo(PackInfo *stats){
  int j;
  if (stats){
    for (j = 0; j < stats->n_models; j ++)
      if (stats->nn[j])
	delete [] stats->nn[j];
    delete [] stats->nn;
    delete [] stats->uu;
    delete [] stats->ii;
    delete stats;
  }
}

extern "C" void set_packinfo_uu(PackInfo *stats, int n, int uu){
  assert(stats && uu > 0 && n >= 0 && n < stats->n_models);
  stats->uu[n] = uu;
}

extern "C" void set_packinfo_ii(PackInfo *stats, int n, int ii){
  assert(stats && ii > 0 && n >= 0 && n < stats->n_models);
  stats->ii[n] = ii;
  if (stats->nn[n]) free(stats->nn[n]);
  stats->nn[n] = new int[ii];
}

extern "C" void set_packinfo_nn(PackInfo *stats, int n, int i, int nn){
  assert(stats && n >= 0 && n < stats->n_models 
	 && i >= 0 && i < stats->ii[n] && nn > 0);
  stats->nn[n][i] = nn;
}

adSentence initializeAdSentence(int ww, adModel **ws){
  adSentence s = new adSentenceStruct;
  s->ww = ww;
  s->ws = new adModel*[ww];
  int w;
  for (w = 0; w < ww; w ++)
    s->ws[w] = ws[w];
  return s;
}

void freeAdSentence(adSentence s){
  delete [] s->ws;
  delete s;
}

adModel *unpack_one(Real *paras, int uu, int ii, int *nn, char *model_name,
		    enum part_of_speech model_pos, int model_n_roles, int offset){
  adModel *m = allocAdModel(ii, uu);
  adFfm *ffm;
  adDParam *p;
  adtl::adouble **a, *b;
  int u, v, i, k, kk, cnt = 0;

  ad_set_model_name(m, model_name);
  m->n_roles = model_n_roles;
  m->pos = model_pos;
  /* hmm parameters */
  a = MATRIX(m->hmm->logA);
  b = VECTOR(m->hmm->logB);
  for (u = 0; u < uu; u ++){
    assert(paras[u] >= 0 && paras[u] <= 1);  
    b[u] = paras[u];
#pragma omp critical(setadvalue)
    {
      b[u].setADValue(offset + cnt, 1);
    }
    b[u] = log(b[u]);
    cnt ++;
  }
  paras += uu;
  for (u = 0; u < uu; u ++){
    for (v = 0; v < uu; v ++){
      assert(paras[v] >= 0 && paras[v] <= 1); 
      a[u][v] = paras[v];
#pragma omp critical(setadvalue)
      {
	a[u][v].setADValue(offset + cnt, 1);
      }
      a[u][v] = log(a[u][v]);
      cnt ++;
    }
    paras += uu;
  }
  /* ffm parameters */
  for (u = 0; u < uu; u ++){
    ffm = m->ffm[u];
    for (i = 0; i < ii; i ++){
      kk = nn[i];
      ad_setFeatKK(ffm, i, kk);
      p = ffm->p[i];
      for (k = 0; k < kk; k ++){
	assert(paras[k] >= 0 && paras[k] <= 1);  
	p->p[k] = paras[k];
#pragma omp critical(setadvalue)
	{
	  (p->p[k]).setADValue(offset + cnt, 1);
	}
	cnt ++;
      }
      paras += kk;
    }
  }
  return m;
}

adModel **unpack
(Real *paras, PackInfo *stats, char **model_names, enum part_of_speech *model_pos, 
 int *model_n_roles, int *selected, int *block_size){

  adModel **ms = new adModel*[stats->n_models];
  int n, uu, ii, offset = 0;
  for (n = 0; n < stats->n_models; n ++){
    uu = stats->uu[n];
    ii = stats->ii[n];
    if (selected[n] == 0)
      ms[n] = NULL;
    else{
      ms[n] = unpack_one(paras, uu, ii, stats->nn[n], model_names[n],
			 model_pos[n], model_n_roles[n], offset);
      offset += block_size[n];
    }
    paras += block_size[n];
  }
  return ms;
}

void sentence_derivatives
(Real *paras, int n_paras, int *sentence_indices, int ww, int nTracks, IVec *roles, 
 BoxesMovie bm, Flow **flow_movie, int fl_tt, Real scale, PackInfo *stats, 
 char **model_names, enum part_of_speech *model_pos, int *model_n_roles, char **objects, 
 int ol, FeatureMedoid **fms, int mn, enum model_constraint model_constraint,
 /* Output */ Real *gradients){

  int w, idx, n, i, offset, *selected, *block_size, ptr;
  const Real *real_gradients;
  adtl::adouble alikelihood;
  adModel **ms;
  adModel **sentence = new adModel*[ww];
  adSentence s;
  
  selected = new int[stats->n_models];
  block_size = new int[stats->n_models];
  memset(selected, 0, stats->n_models * sizeof(int));
  for (w = 0; w < ww; w ++){
    idx = sentence_indices[w];
    assert(idx >= 0 && idx < stats->n_models);
    selected[idx] = 1;
  }

  for (n = 0; n < stats->n_models; n ++){
    block_size[n] = stats->uu[n] * (stats->uu[n] + 1);
    for (i = 0; i < stats->ii[n]; i ++)
      block_size[n] += stats->nn[n][i] * stats->uu[n];
  }

  ms = unpack(paras, stats, model_names, model_pos, model_n_roles, selected, block_size);
  for (w = 0; w < ww; w ++){
    idx = sentence_indices[w];
    sentence[w] = ms[idx];
  }
  s = initializeAdSentence(ww, sentence);
  alikelihood = ad_sentence_likelihood_one(s, nTracks, roles, bm, flow_movie, fl_tt, scale, 
					   objects, ol, fms, mn, model_constraint);
  alikelihood = exp(alikelihood);
  
  real_gradients = alikelihood.getADValue();
  
  memset(gradients, 0, n_paras * sizeof(Real));
  /* unzip the parameters */
  ptr = 0;
  offset = 0;
  for (n = 0; n < stats->n_models; n ++){
    if (selected[n] == 1){
      memcpy(gradients + offset, real_gradients + ptr, block_size[n] * sizeof(Real));
      ptr += block_size[n];
    }
    offset += block_size[n];
  }

  /* Clear up */
  for (n = 0; n < stats->n_models; n ++)
    if (ms[n])
      freeAdModel(ms[n]);
  delete [] ms;
  delete [] sentence;
  delete [] selected;
  delete [] block_size;
  freeAdSentence(s);
}

/* Calculate the derivatives of the likelihood for each sentence */
extern "C" Real *sentence_derivatives_one_video
(Real *paras, int nSentences, int n_paras, int *sentence_indices, int *ww,
 int *nTracks, IVec *roles, BoxesMovie bm, Flow **flow_movie, int fl_tt,
 Real scale, PackInfo *stats, char **model_names, enum part_of_speech *model_pos,
 int *model_n_roles, char **objects, int ol, FeatureMedoid **fms, int mn,
 enum model_constraint model_constraint){
  
  Real *derivatives_multiple = (Real *)malloc(nSentences * n_paras * sizeof(Real));
  int n, *offset = new int[nSentences];
  offset[0] = 0;
  for (n = 1; n < nSentences; n ++)
    offset[n] = offset[n - 1] + ww[n - 1];

#pragma omp parallel ADOLC_OPENMP
  {
#pragma omp for private(n)
    for (n = 0; n < nSentences; n ++){
      sentence_derivatives(paras, n_paras, sentence_indices + offset[n], ww[n], nTracks[n],
			   roles + offset[n], bm, flow_movie, fl_tt, scale, stats, model_names, model_pos, 
			   model_n_roles, objects, ol, fms, mn, model_constraint, derivatives_multiple + n*n_paras);
    }
  }
  delete [] offset;
  return derivatives_multiple;
}
