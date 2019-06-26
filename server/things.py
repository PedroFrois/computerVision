import falcon
import json
from scipy.optimize import curve_fit
import numpy as np


def sigm(x,k,x0):
    return 1/(1+np.exp(-k*(x-x0)))

class SigmoidFit:
    def on_post(self,req,resp):
        resp.status=falcon.HTTP_200
        string_param = req.stream.read().decode("utf-8")
        print(string_param)
        params_array = string_param.replace("%3B",";").split("&")
        params = {}
        for p in params_array:
            if(len(p)>0):
                aux = p.split("=")
                params[aux[0]] = [float(x) for x in aux[1].split(";")]
        print(params)
        x = params["index"]
        y = params["mean"]
        popt,pcov = curve_fit(sigm,x,y,bounds=([-np.inf,1],[np.inf,np.inf]))
        
        resp.body = str({"k":popt[0],"x0":int(popt[1])})

fit = SigmoidFit()

# falcon.API instances are callable WSGI apps
app = falcon.API()
# things will handle all requests to the '/things' URL path
app.add_route('/fit', fit)