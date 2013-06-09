#include "cv.h"
#include "highgui.h"
#define     NO_OBJECT       0
#define     MIN(x, y)       (((x) < (y)) ? (x) : (y))
#define     ELEM(img, r, c) (CV_IMAGE_ELEM(img, unsigned char, r, c))
#define     ONETWO(L, r, c, col) (L[(r) * (col) + c])

extern "C"
int find( int set[], int x )
{
	int r = x;
	while ( set[r] != r )
		r = set[r];
	return r;
}
/*
labeling scheme
+-+-+-+
|D|C|E|
+-+-+-+
|B|A| |
+-+-+-+
| | | |
+-+-+-+
A is the center pixel of a neighborhood.  In the 3 versions of
connectedness:
4:  A connects to B and C
6:  A connects to B, C, and D
8:  A connects to B, C, D, and E
*/

extern "C"
int bwlabel(IplImage* img, int n, int* labels)
{
	if(n != 4 && n != 8)
		n = 4;
	int nr = img->height;
	int nc = img->width;
	int total = nr * nc;
	// results
	memset(labels, 0, total * sizeof(int));
	int nobj = 0;                               // number of objects found in image
	// other variables                             
	int* lset = new int[total];   // label table
	memset(lset, 0, total * sizeof(int));
	int ntable = 0;
	for( int r = 0; r < nr; r++ ) 
	{
		for( int c = 0; c < nc; c++ ) 
		{            
			if ( ELEM(img, r, c) )   // if A is an object
			{               
				// get the neighboring pixels B, C, D, and E
				int B, C, D, E;
				if ( c == 0 ) 
					B = 0; 
				else 
					B = find( lset, ONETWO(labels, r, c - 1, nc) );
				if ( r == 0 ) 
					C = 0; 
				else 
					C = find( lset, ONETWO(labels, r - 1, c, nc) );
				if ( r == 0 || c == 0 ) 
					D = 0; 
				else 
					D = find( lset, ONETWO(labels, r - 1, c - 1, nc) );
				if ( r == 0 || c == nc - 1 ) 
					E = 0;
				else 
					E = find( lset, ONETWO(labels, r - 1, c + 1, nc) );
				if ( n == 4 ) 
				{
					// apply 4 connectedness
					if ( B && C ) 
					{        // B and C are labeled
						if ( B == C )
							ONETWO(labels, r, c, nc) = B;
						else {
							lset[C] = B;
							ONETWO(labels, r, c, nc) = B;
						}
					} 
					else if ( B )             // B is object but C is not
						ONETWO(labels, r, c, nc) = B;
					else if ( C )               // C is object but B is not
						ONETWO(labels, r, c, nc) = C;
					else 
					{                      // B, C, D not object - new object
						//   label and put into table
						ntable++;
						ONETWO(labels, r, c, nc) = lset[ ntable ] = ntable;
					}
				} 
				else if ( n == 6 ) 
				{
					// apply 6 connected ness
					if ( D )                    // D object, copy label and move on
						ONETWO(labels, r, c, nc) = D;
					else if ( B && C ) 
					{        // B and C are labeled
						if ( B == C )
							ONETWO(labels, r, c, nc) = B;
						else 
						{
							int tlabel = MIN(B,C);
							lset[B] = tlabel;
							lset[C] = tlabel;
							ONETWO(labels, r, c, nc) = tlabel;
						}
					} 
					else if ( B )             // B is object but C is not
						ONETWO(labels, r, c, nc) = B;
					else if ( C )               // C is object but B is not
						ONETWO(labels, r, c, nc) = C;
					else 
					{                      // B, C, D not object - new object
						//   label and put into table
						ntable++;
						ONETWO(labels, r, c, nc) = lset[ ntable ] = ntable;
					}
				}
				else if ( n == 8 ) 
				{
					// apply 8 connectedness
					if ( B || C || D || E ) 
					{
						int tlabel = B;
						if ( B ) 
							tlabel = B;
						else if ( C ) 
							tlabel = C;
						else if ( D ) 
							tlabel = D;
						else if ( E ) 
							tlabel = E;
						ONETWO(labels, r, c, nc) = tlabel;
						if ( B && B != tlabel ) 
							lset[B] = tlabel;
						if ( C && C != tlabel ) 
							lset[C] = tlabel;
						if ( D && D != tlabel ) 
							lset[D] = tlabel;
						if ( E && E != tlabel ) 
							lset[E] = tlabel;
					} 
					else 
					{
						//   label and put into table
						ntable++;
						ONETWO(labels, r, c, nc) = lset[ ntable ] = ntable;
					}
				}
			} 
			else 
			{
				ONETWO(labels, r, c, nc) = NO_OBJECT;      // A is not an object so leave it
			}
		}
	}
	// consolidate component table
	for( int i = 0; i <= ntable; i++ )
		lset[i] = find( lset, i );                                                                                                 
	// run image through the look-up table
	for( int r = 0; r < nr; r++ )
		for( int c = 0; c < nc; c++ )
			ONETWO(labels, r, c, nc) = lset[ ONETWO(labels, r, c, nc) ];
	// count up the objects in the image
	for( int i = 0; i <= ntable; i++ )
		lset[i] = 0;
	for( int r = 0; r < nr; r++ )
		for( int c = 0; c < nc; c++ )
			lset[ ONETWO(labels, r, c, nc) ]++;
	// number the objects from 1 through n objects
	nobj = 0;
	lset[0] = 0;
	for( int i = 1; i <= ntable; i++ )
		if ( lset[i] > 0 )
			lset[i] = ++nobj;
	// run through the look-up table again
	for( int r = 0; r < nr; r++ )
		for( int c = 0; c < nc; c++ )
			ONETWO(labels, r, c, nc) = lset[ ONETWO(labels, r, c, nc) ];
	//
	delete[] lset;
	return nobj;
}

