/* LaHaShem HaAretz U'Mloah */

#ifndef HMM_SENTENCE_H
#define HMM_SENTENCE_H

/* Macros */

#define NEGATIVE_INFINITY (-1.0/0.0)
#define LOG_MATH_PRECISION 35.0
#define CORRECTION_EPS 1e-30
#define ISNAN(x) (x != x)
#define DISTANCE_2D(x1, y1, x2, y2) \
  sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))
#ifndef PI
#define PI 3.14159265358979323846
#endif

#define VLENGTH(v) ((v)->x)
#define ROWS(m) ((m)->y)
#define COLUMNS(m) ((m)->x)
#define VECTOR(x) ((x)->v)
#define MATRIX(x) ((x)->m)

#ifdef _______FLOAT_REAL
typedef float Real;
#else
typedef double Real;
#endif

#define MACH_EPS 1e-7
#define IS_ZERO(x) (-MACH_EPS<(x)&&(x)<MACH_EPS)

#ifndef MIN
#define MIN(a,b) ((a)>(b)?(b):(a))
#endif
#ifndef MAX
#define MAX(a,b) ((a)>(b)?(a):(b))
#endif

#ifndef TRUE
#define TRUE (0==0)
#endif

#ifndef FALSE
#define FALSE (0!=0)
#endif

#define safe_malloc(x) _safe_malloc(x, __LINE__, __FILE__)

#define MAXIT 60
#define UNUSED (-1.11e30)
#define SIGN(a,b) ((b)>0.0?fabs(a):-fabs(a))

enum one_track_feature_type {FLOW_MAGNITUDE_IN_BOX, FLOW_ORIENTATION_IN_BOX, DISPLACEMENT,
			     POSITION_X, POSITION_Y, UPPER_Y, AREA, MODEL_NAME, MODEL_COLOR};

enum part_of_speech {NOUN, VERB, ADVERB, ADJECTIVE, PREPOSITION, MOTION_PREPOSITION, OTHER};

enum training_mode {HMM_ML, HMM_DT, HMM_MIXED, HMM_MIXED_ML};

enum model_constraint {NO_DUPLICATE_MODELS, NO_MODEL_CONSTRAINT};

struct RVecStruct {int x; Real *v;};
struct IVecStruct {int x; int *v;};
struct RMatStruct {int x; int y; Real **m;};
struct RMat3dStruct {int x; int y; int z; Real ***m;};

typedef struct RVecStruct *RVec;
typedef struct IVecStruct *IVec;
typedef struct RMatStruct *RMat;
typedef struct RMat3dStruct *RMat3d;

typedef struct {
  Real discrimination;
  Real likelihood;
} UpdateResults;

/* A similar struct w.r.t voc4-detection in scheme */
typedef struct {
  Real x1, y1, x2, y2;
  void *parts;               
  int filter;                
  Real strength;
  int delta;
  int color;
  char *model;
} Box;

struct TrackStruct {
  int tt;
  Box **ds;
};

struct BoxesMovieStruct {
  int tt;                     /* The movie length */
  int ii;                     /* The number of boxes in one frame */
  Box ***ds;
};

typedef struct TrackStruct *Track;
typedef struct BoxesMovieStruct *BoxesMovie;

/* Auxiliary function */
Real my_exp(Real x);
Real my_log(Real x);
Real my_atan2(Real x, Real y);
RMat *allocate_rmat_vector(int n);
RVec allocRVec(int x);
IVec allocIVec(int x);
void setIVec(IVec v, int i, int x);
RMat allocRMat(int y, int x);
RMat3d allocRMat3d(int z, int y, int x);
void free_rmat_vector(RMat *v);
void freeRVec(RVec v);
void freeIVec(IVec v);
void freeRMat(RMat m);
void freeRMat3d(RMat3d m);
Real *addRVec(Real *u, const Real *v, int size);
Real *copyRVec(Real *u, const Real *v, int size);
Real dotProdRVec(const Real *u, const Real *v, int size);
Real normaliseRVec(Real *v, int size);
Real rmat_get(RMat rmat, int i, int j);
void rmat_set(RMat rmat, int i, int j, Real x);
void rmat_vector_set(RMat *v, int i, RMat r);
Real *scaleRVec(Real *v, Real k, int size);
Real sumOfLogs(Real *v, int size);
Real sumRVec(Real *v, int size);
Real add_exp(Real e1, Real e2);
Real sigmoid(Real t, Real a, Real b);
float lbessi0(float x);
float lbessi1(float x);
float zriddr(float (*func)(float), float x1, float x2, float xacc);
void free_c_vector(void *v);
int c_int_vector_ref(int *v, int x);
void *_safe_malloc(long size, int line, char *file);
//void panic(const char *error_text, ...);

/* Results access function */
Real update_results_discrimination(UpdateResults *res);
Real update_results_likelihood(UpdateResults *res);

/* Box access function */

Box *allocBox();
Real box_coordinates(Box *d, int i);
int box_filter(Box *d);
Real box_strength(Box *d);
int box_delta(Box *d);
int box_color(Box *d);
char *box_model(Box *d);
void set_box_coordinates(Box *d, int i, Real x);
void set_box_filter(Box *d, int filter);
void set_box_strength(Box *d, Real strength);
void set_box_delta(Box *d, int delta);
void set_box_color(Box *d, int color);
void set_box_model(Box *d, char *model);
void freeBox(Box *d);
Real box_center(Box *d, int flag);
Real box_center_x(Box *d);
Real box_center_y(Box *d);
int box_area(Box *d);
Real unpack_box_color_h(Box *d);
Real box_distance(Box *d1, Box *d2);
Real box_overlap_percentage(Box *d1, Box *d2);
Box *box_scale(Box *d, Real scale);
Box *box_shift(Box *d, Real shift_x, Real shift_y);

/* Track access funcion */
Track allocTrack(int tt);
void set_track_boxes(Track tr, int tt, Box **ds);
void freeTrack(Track tr);

/* BoxesMovie access function */
BoxesMovie allocBoxesMovie(int tt, int ii);
void set_boxes_movie(BoxesMovie bm, int t, int i, Box *d);
void freeBoxesMovie(BoxesMovie bm);

Real SQR(Real arg);

#endif

/* Tam V'Nishlam Shevah L'El Borei Olam */ 
