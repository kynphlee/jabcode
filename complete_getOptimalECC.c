void getOptimalECC(jab_int32 capacity, jab_int32 net_data_length, jab_int32* wcwr)
{
	jab_float min = capacity;
	for (jab_int32 k=3; k<=6+2; k++)
	{
		for (jab_int32 j=k+1; j<=6+3; j++)
		{
			jab_int32 dist = (capacity/j)*j - (capacity/j)*k - net_data_length; //max_gross_payload = floor(capacity / wr) * wr
			if(dist<min && dist>=0)
			{
				wcwr[1] = j;
				wcwr[0] = k;
				min = dist;
			}
		}
	}
}

/**
 * @brief Encode the input data
 * @param data the character input data
