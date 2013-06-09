function I = intImg(A)
%Computes an integral image of a two-dimensional matrix
%The integral image of matrix A(a,b) is defined such that I(c,d) is equal
%to the sum of all values in A where a<=c and b<=d
%ie, I(x,y) is the sum of all pixels in A which are above and to the left
%of (x,y), inclusive.
%This makes it possible to easily find the sum of all points in a 
%rectangular region of A:
%sum = I(top left) + I(bottom right) - I(top right) - I(bottom left)

I = cumsum(cumsum(A),2);