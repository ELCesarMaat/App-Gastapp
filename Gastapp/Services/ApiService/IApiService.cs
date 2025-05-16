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

        [Post("/User/CreateUser")]
        public Task<string> CreateUser(User user);


        [Post("/Spendings/SyncNewSpendings")]
        public Task<bool> SyncNewSpendings(List<SpendingDto> spendings);

        [Post("/Spendings/SyncNewCategories")]
        public Task<bool> SyncNewCategories(List<CategoryDto> spendings);
    }
}
