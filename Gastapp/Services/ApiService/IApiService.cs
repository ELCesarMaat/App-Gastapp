using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Gastapp.Models;
using Refit;

namespace Gastapp.Services.ApiService
{
    public interface IApiService
    {

        [Get("/User/CreateUser")]
        public Task<string> CreateUser(User user);
    }
}
