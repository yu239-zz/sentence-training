/* LaHaShem HaAretz U'Mloah */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <assert.h>
#include <math.h>
#include "hmm-def.h"

extern void panic(const char *error_text, ...);

Real update_results_discrimination(UpdateResults *res){
  return res->discrimination;
}

Real update_results_likelihood(UpdateResults *res){
  return res->likelihood;
}

Box *allocBox(){
  Box *d = safe_malloc(sizeof *d);
  d->parts = NULL;
  d->model = NULL;
  return d;
}
 
Real box_coordinates(Box *d, int i){
  Real x = 0;
  assert(d != NULL);
  switch(i){
  case 0: x = d->x1; break;
  case 1: x = d->y1; break;
  case 2: x = d->x2; break;
  case 3: x = d->y2; break;
  default: panic("box_coordinates: i = %d out of range [0, 3]\n", i);
  }
  return x;
}

int box_filter(Box *d){
  assert(d != NULL);
  return d->filter;
}

Real box_strength(Box *d){
  assert(d != NULL);
  return d->strength;
}

int box_delta(Box *d){
  assert(d != NULL);
  return d->delta;
}

int box_color(Box *d){
  assert(d != NULL);
  return d->color;
}

char *box_model(Box *d){
  assert(d && d->model);
  return d->model;
}

void set_box_coordinates(Box *d, int i, Real x){
  assert(d != NULL);
  /* Box coordinates produced by the detector may be negative */
  if (x < -1)
    panic("set_box_coordinates: x = %0.4lf out of range [-1, +oo]\n", x);
  x = MAX(0, x);
  switch(i){
  case 0: d->x1 = x; break;
  case 1: d->y1 = x; break;
  case 2: d->x2 = x; break;
  case 3: d->y2 = x; break;
  default: panic("set_box_coordinates: i = %d out of range [0, 3]\n", i);
  }
}

void set_box_filter(Box *d, int filter){
  assert(d != NULL);
  d->filter = filter;
}

void set_box_strength(Box *d, Real strength){
  assert(d != NULL);
  d->strength = strength;
}

void set_box_delta(Box *d, int delta){
  assert(d != NULL);
  if (delta < 0)
    panic("set_box_delta: delta = %d out of range [0, +oo]\n", delta);
  d->delta = delta;
}

void set_box_color(Box *d, int color){
  assert(d != NULL && color >= 0);
  d->color = color;
}

void set_box_model(Box *d, char *model){
  assert(d != NULL && model != NULL);
  if (d->model) free(d->model);
  d->model = safe_malloc((strlen(model) + 1));
  strcpy(d->model, model);
}

Real box_center_x(Box *d){
  return box_center(d, 0);
}

Real box_center_y(Box *d){
  return box_center(d, 1);
}

Real box_center(Box *d, int flag){
  Real mid = 0;
  switch(flag){
  case 0: mid = (d->x1 + d->x2) / 2;
    break;
  case 1: mid = (d->y1 + d->y2) / 2;
    break;
  default: panic("box_center: flag = %d", flag);
  }
  return mid;
}

int box_area(Box *d){
  return (d->x2 - d->x1 + 1) * (d->y2 - d->y1 + 1);
}

Real unpack_box_color_h(Box *d){
  Real c = d->color;
  int degree = (int)(c/(100*100)) % 360;
  return PI / 180 * degree;
}

void freeBox(Box *d){
  if (d != NULL){
    assert(d->parts == NULL);
    if (d->model) free(d->model);
    free(d);
  }
}

Real box_distance(Box *d1, Box *d2){
  return DISTANCE_2D(box_center_x(d1), box_center_y(d1),
		     box_center_x(d2), box_center_y(d2));
}

Real box_overlap_percentage(Box *d1, Box *d2){
  int x11, x12, x21, x22, y11, y12, y21, y22;
  int x_overlap, y_overlap;
  x11 = MIN(d1->x1, d1->x2);
  x12 = MAX(d1->x1, d1->x2);
  y11 = MIN(d1->y1, d1->y2);
  y12 = MAX(d1->y1, d1->y2);
  x21 = MIN(d2->x1, d2->x2);
  x22 = MAX(d2->x1, d2->x2);
  y21 = MIN(d2->y1, d2->y2);
  y22 = MAX(d2->y1, d2->y2);
  x_overlap = MAX(0, MIN(x12, x22) - MAX(x11, x21));
  y_overlap = MAX(0, MIN(y12, y22) - MAX(y11, y21));
  return (Real)(x_overlap * y_overlap)
    / MIN((x12 - x11) * (y12 - y11), (x22 - x21) * (y22 - y21));
}

Box *box_scale(Box *d, Real scale){
  Box *new_d = allocBox();
  new_d->x1 = scale * d->x1;
  new_d->y1 = scale * d->y1;
  new_d->x2 = scale * d->x2;
  new_d->y2 = scale * d->y2;
  return new_d;
}

Box *box_shift(Box *d, Real shift_x, Real shift_y){
  Box *new_d = allocBox();
  new_d->x1 = d->x1 + shift_x;
  new_d->y1 = d->y1 + shift_y;
  new_d->x2 = d->x2 + shift_x;
  new_d->y2 = d->y2 + shift_y;
  return new_d;
}

Track allocTrack(int tt){
  Track tr = safe_malloc(sizeof *tr);
  tr->tt = tt;
  tr->ds = safe_malloc(tt * sizeof *(tr->ds));
  return tr;
}

void set_track_boxes(Track tr, int tt, Box **ds){
  assert(tr != NULL && ds != NULL);
  assert(tt > 0 && ds[0] != NULL);
  assert(tr->tt == tt);
  int t;
  for (t = 0; t < tt; t ++)
    tr->ds[t] = ds[t];
}

void freeTrack(Track tr){
  if (tr != NULL){
    free(tr->ds);
    free(tr);
  }
}

BoxesMovie allocBoxesMovie(int tt, int ii){
  assert(tt > 0);
  BoxesMovie bm = safe_malloc(sizeof *bm);
  int t;
  bm->tt = tt;
  bm->ii = ii;
  bm->ds = safe_malloc(tt * sizeof *(bm->ds));
  for (t = 0; t < tt; t ++)
    bm->ds[t] = safe_malloc(ii * sizeof *(bm->ds[t]));
  return bm;
}

void set_boxes_movie(BoxesMovie bm, int t, int i, Box *d){
  assert(bm != NULL);
  if (t < 0 || t >= bm->tt)
    panic("set_boxes_movie: t = %d out of range [0, %d)", t, bm->tt);
  if (i < 0 || i >= bm->ii)
    panic("set_boxes_movie: i = %d out of range [0, %d)", i, bm->ii);
  bm->ds[t][i] = d;
}

void freeBoxesMovie(BoxesMovie bm){
  int t;
  if (bm != NULL){
    for (t = 0; t < bm->tt; t ++)
      free(bm->ds[t]);
    free(bm->ds);
    free(bm);
  }
}

/* needs work : maybe shouldn't check exact equality with NEGATIVE_INFINITY */
Real sigmoid(Real x, Real a, Real b){
  return 1.0 / (1 + my_exp(- b * (x - a)));
}

Real add_exp(Real e1, Real e2)
{ Real e_max = MAX(e1, e2), e_min = MIN(e1, e2);
  return (e_max==NEGATIVE_INFINITY)? NEGATIVE_INFINITY:
    (e_max-e_min>LOG_MATH_PRECISION)? e_max:
    my_log(1.0+my_exp(e_min-e_max))+e_max;}

Real my_exp(Real x)
{ if (x == NEGATIVE_INFINITY)
    return 0.0;
  return exp(x);}

Real my_log(Real x)
{ if (x==0.0) return NEGATIVE_INFINITY;
  else return log(x);}

Real my_atan2(Real x, Real y)
{ if (x==0.0&&y==0.0) return 0.0;
  else if (x==0.0&&y==-1.0) return -PI;
  else return atan2(x, y);}

RMat *allocate_rmat_vector(int n)
{ RMat *v = safe_malloc(sizeof *v * n);
  return v;}

RVec allocRVec(int x)
{ RVec v = safe_malloc(sizeof *v);
  VECTOR(v) = safe_malloc(x * sizeof *(VECTOR(v)));
  VLENGTH(v) = x;
  return v;}

IVec allocIVec(int x)
{ IVec v = safe_malloc(sizeof *v);
  VECTOR(v) = safe_malloc(x * sizeof *(VECTOR(v)));
  VLENGTH(v) = x;
  return v;}

void setIVec(IVec v, int i, int x){
  assert(i >= 0 && i < VLENGTH(v));
  VECTOR(v)[i] = x;
}

RMat allocRMat(int y, int x)
{ int i;
  RMat m = safe_malloc(sizeof *m);
  MATRIX(m) = safe_malloc(y * sizeof *(MATRIX(m)));
  for (i = 0; i<y; i++) MATRIX(m)[i] = safe_malloc(x * sizeof *(MATRIX(m)[i]));
  ROWS(m) = y;
  COLUMNS(m) = x;
  return m;}

RMat3d allocRMat3d(int z, int y, int x)
{ int i, j;
  RMat3d m = safe_malloc(sizeof *m);
  MATRIX(m) = safe_malloc(z * sizeof *(MATRIX(m)));
  for (i = 0; i<z; i++)
    { MATRIX(m)[i] = safe_malloc(y * sizeof *(MATRIX(m)[i]));
    for (j = 0; j<y; j++)
      { MATRIX(m)[i][j] = safe_malloc(x * sizeof *(MATRIX(m)[i][j]));}}
  m->z = z;
  m->y = y;
  m->x = x;
  return m;}

void free_rmat_vector(RMat *v) {free(v);}

void freeRVec(RVec v)
{ if (v!=NULL)
  { if (VECTOR(v)!=NULL) free(VECTOR(v));
    free(v);}}

void freeIVec(IVec v)
{ if (v!=NULL)
  { if (VECTOR(v)!=NULL) free(VECTOR(v));
    free(v);}
}

void freeRMat(RMat m)
{ int i;
  if (m!=NULL)
  { if (MATRIX(m)!=NULL)
    { for (i = 0; i<ROWS(m); i++)
      { if (MATRIX(m)[i]!=NULL) free(MATRIX(m)[i]);}
      free(MATRIX(m));}
    free(m);}}

void freeRMat3d(RMat3d m)
{ int i, j;
  if (m!=NULL)
  { if (MATRIX(m)!=NULL)
    { for (i = 0; i<m->z; i++)
      { if (MATRIX(m)[i]!=NULL)
	{ for (j = 0; j<m->y; j++)
	  { if (MATRIX(m)[i][j]!=NULL) free(MATRIX(m)[i][j]);}
	  free(MATRIX(m)[i]);}}
      free(MATRIX(m));}
    free(m);}}

/* vector operations */

Real *addRVec(Real *u, const Real *v, int size)
{ for (size--; size>=0; size--) u[size] += v[size];
  return u;}

Real *copyRVec(Real *u, const Real *v, int size)
{ for (size--; size>=0; size--) u[size] = v[size];
  return u;}

Real dotProdRVec(const Real *u, const Real *v, int size)
{ Real x = 0.0;
  for (size--; size>=0; size--) x += u[size]*v[size];
  return x;}

/* normaliseRVec -
 * scales a vector so that the values in the vector lie in the range of
 * -1 to 1.  The scaled vector will only contain positive (or negative) values
 * if the original vector contained positive (or negative) values.  Returns
 * the maximum absolute value in the vector */

Real normaliseRVec(Real *v, int size)
{ int i;
  Real max = (size>0)?fabs(v[0]):0.0;
  for (i = 1; i<size; i++) if (fabs(v[i])>max) max = fabs(v[i]);
  if (max!=0.0) for (i = 0; i<size; i++) v[i] /= max;
  return max;}

Real rmat_get(RMat rmat, int i, int j)
{ if (i<0||i>=ROWS(rmat)) printf("i=%d out of bounds (0,%d)", i, ROWS(rmat));
  if (j<0||j>=COLUMNS(rmat))
  { printf("j=%d out of bounds (0,%d)", i, COLUMNS(rmat));}
  return MATRIX(rmat)[i][j];}

void rmat_set(RMat rmat, int i, int j, Real x)
{ if (i<0||i>=ROWS(rmat)) printf("i=%d out of bounds (0,%d)", i, ROWS(rmat));
  if (j<0||j>=COLUMNS(rmat))
  { printf("j=%d out of bounds (0,%d)", i, COLUMNS(rmat));}
  MATRIX(rmat)[i][j] = x;}

void rmat_vector_set(RMat *v, int i, RMat r) {v[i] = r;}

/* scaleRVec - v = k*v */

Real *scaleRVec(Real *v, Real k, int size)
{ for (size--; size>=0; size--) v[size] *= k;
  return v;}

/* sumOfLogs - x = log(exp(v[1]) + exp(v[2]) + ... + exp(v[size])) */

Real sumOfLogs(Real *v, int size)
{ Real x = NEGATIVE_INFINITY;
  for (size--; size>=0; size--) x = add_exp(x, v[size]);
  return x;}

Real sumRVec(Real *v, int size)
{ Real x = 0.0;
  for (size--; size>=0; size--) x += v[size];
  return x;}

/* (C) Copr. 1986-92 Numerical Recipes Software >. */

float lbessi0(float x)
{ float ax;
  double y;
  if ((ax = fabs(x))<3.75)
  { y = x/3.75;
    y *= y;
    return my_log(1.0+y*(3.5156229+y*(3.0899424+y*(1.2067492
		  +y*(0.2659732+y*(0.360768e-1+y*0.45813e-2))))));}
  else
  { y = 3.75/ax;
    return (ax-0.5*my_log(ax))+
	   my_log(0.39894228+y*(0.1328592e-1
		  +y*(0.225319e-2+y*(-0.157565e-2+y*(0.916281e-2
		  +y*(-0.2057706e-1+y*(0.2635537e-1+y*(-0.1647633e-1
		  +y*0.392377e-2))))))));}}

float lbessi1(float x)
{ float ax;
  double y;
  assert(x>=0.0);
  if ((ax = fabs(x))<3.75)
  { y = x/3.75;
    y *= y;
    return my_log(ax*(0.5+y*(0.87890594+y*(0.51498869+y*(0.15084934
    		  +y*(0.2658733e-1+y*(0.301532e-2+y*0.32411e-3)))))));}
  else
  { y = 3.75/ax;
    return (ax-0.5*my_log(ax))
	    +my_log(0.39894228+y*(-0.3988024e-1+y*(-0.362018e-2
		    +y*(0.163801e-2+y*(-0.1031555e-1+y*(0.2282967e-1
		    +y*(-0.2895312e-1+y*(0.1787654e-1-y*0.420059e-2))))))));}}

float zriddr(float (*func)(float), float x1, float x2, float xacc)
{ int j;
  float ans, fh, fl, fm, fnew, s, xh, xl, xm, xnew;
  fl = (*func)(x1);
  fh = (*func)(x2);
  if ((fl>0.0&&fh<0.0)||(fl<0.0&&fh>0.0))
  { xl = x1;
    xh = x2;
    ans = UNUSED;
    for (j = 1; j<=MAXIT; j++)
    { xm = 0.5*(xl+xh);
      fm = (*func)(xm);
      s = sqrt(fm*fm-fl*fh);
      if (s==0.0) return ans;
      xnew = xm+(xm-xl)*((fl>=fh?1.0:-1.0)*fm/s);
      if (fabs(xnew-ans)<=xacc) return ans;
      ans = xnew;
      fnew = (*func)(ans);
      if (fnew==0.0) return ans;
      if (SIGN(fm, fnew)!=fm)
      { xl = xm;
	fl = fm;
	xh = ans;
	fh = fnew;}
      else if (SIGN(fl, fnew)!=fl)
      { xh = ans;
	fh = fnew;}
      else if (SIGN(fh, fnew)!=fh)
      { xl = ans;
	fl = fnew;}
      else printf("never get here.");
      if (fabs(xh-xl)<=xacc) return ans;}
    printf("zriddr exceed maximum iterations");}
  else
  { if (fl==0.0) return x1;
    if (fh==0.0) return x2;
    printf("root must be bracketed in zriddr. (l, h) = %f %f", fl, fh);}
  return 0.0;}

void free_c_vector(void *v) {free(v);}

void *_safe_malloc(long size, int line, char *file)
{ void *buffer;
  if (!(buffer = malloc(size)))
  { fprintf(stderr, "%s(%d): _safe_malloc, memory allocation failure. %ld bytes\n",
	    file, line, size);
   exit(1);}
  return buffer;}

Real SQR(Real a) { return a*a; }

/* Tam V'Nishlam Shevah L'El Borei Olam */
